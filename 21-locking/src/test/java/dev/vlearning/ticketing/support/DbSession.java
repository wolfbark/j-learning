package dev.vlearning.ticketing.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

/**
 * One database session you drive by hand, statement by statement.
 *
 * <p>Two of these in a test let you write the interleaving out as a script —
 * "T1 reads, T2 reads, T1 writes and commits, T2 writes and commits" — instead
 * of hoping a thread pool produces it. Every statement runs on the session's own
 * thread, so a statement that <em>blocks</em> on a lock can be started, observed
 * as still running ({@link Pending#isBlocked()}), and collected later.
 *
 * <p>This is a test instrument, not a connection pool: it deliberately holds a
 * raw {@link Connection} with autocommit off.
 */
public final class DbSession implements AutoCloseable {

    /** Blocking is a fact to assert, so a statement gets this long to prove it isn't. */
    public static final Duration BLOCKED_AFTER = Duration.ofMillis(500);

    public enum Isolation {

        READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
        REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
        SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

        private final int jdbcLevel;

        Isolation(int jdbcLevel) {
            this.jdbcLevel = jdbcLevel;
        }
    }

    private final String name;
    private final Connection connection;
    private final ExecutorService thread;

    public DbSession(DataSource dataSource, String name) {
        this.name = name;
        this.thread = Executors.newSingleThreadExecutor(r -> new Thread(r, "session-" + name));
        this.connection = call(() -> {
            Connection c = dataSource.getConnection();
            c.setAutoCommit(false);
            return c;
        });
    }

    public DbSession begin(Isolation isolation) {
        return call(() -> {
            connection.setTransactionIsolation(isolation.jdbcLevel);
            return this;
        });
    }

    public long queryLong(String sql, Object... args) {
        return call(() -> {
            try (var ps = prepare(sql, args); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    public String queryText(String sql, Object... args) {
        return call(() -> {
            try (var ps = prepare(sql, args); ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        });
    }

    public int rowCount(String sql, Object... args) {
        return (int) queryLong(sql, args);
    }

    public int update(String sql, Object... args) {
        return call(() -> {
            try (var ps = prepare(sql, args)) {
                return ps.executeUpdate();
            }
        });
    }

    /**
     * Start a statement without waiting for it. Use this whenever the statement
     * may block on another session's lock — which is the interesting case.
     */
    public Pending updateLater(String sql, Object... args) {
        return new Pending(name, thread.submit(() -> {
            try (var ps = prepare(sql, args)) {
                return (Object) ps.executeUpdate();
            }
        }));
    }

    public void commit() {
        call(() -> {
            connection.commit();
            return null;
        });
    }

    public Pending commitLater() {
        return new Pending(name, thread.submit(() -> {
            connection.commit();
            return null;
        }));
    }

    public void rollback() {
        call(() -> {
            connection.rollback();
            return null;
        });
    }

    /** Postgres session settings, e.g. {@code set("lock_timeout", "250ms")}. */
    public void set(String setting, String value) {
        call(() -> {
            try (Statement s = connection.createStatement()) {
                s.execute("SET " + setting + " = '" + value + "'");
            }
            return null;
        });
    }

    @Override
    public void close() {
        try {
            thread.submit(() -> {
                if (!connection.isClosed()) {
                    connection.rollback();
                    connection.close();
                }
                return null;
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // a session left holding a lock is torn down with the container
        } finally {
            thread.shutdownNow();
        }
    }

    private java.sql.PreparedStatement prepare(String sql, Object... args) throws SQLException {
        var ps = connection.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
        return ps;
    }

    @SuppressWarnings("unchecked")
    private <T> T call(SqlCall<T> call) {
        try {
            return (T) thread.submit(call::run).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw SessionException.of(name, e.getCause());
        } catch (TimeoutException e) {
            throw new SessionException(name, "statement still blocked after 30s", null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T run() throws SQLException;
    }

    /** A statement that was started and may or may not have finished. */
    public record Pending(String session, Future<Object> future) {

        /** True when the statement is still waiting — for a lock, in practice. */
        public boolean isBlocked() {
            try {
                future.get(BLOCKED_AFTER.toMillis(), TimeUnit.MILLISECONDS);
                return false;
            } catch (TimeoutException e) {
                return true;
            } catch (ExecutionException e) {
                throw SessionException.of(session, e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        /** Wait for the statement and surface whatever the database made of it. */
        public Object await() {
            try {
                return future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw SessionException.of(session, e.getCause());
            } catch (TimeoutException e) {
                throw new SessionException(session, "statement still blocked after 30s", null, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Carries the SQLSTATE, because in this lesson the five-character code
     * <em>is</em> the diagnosis: {@code 40001} serialisation failure,
     * {@code 40P01} deadlock detected, {@code 55P03} lock not available.
     */
    public static final class SessionException extends RuntimeException {

        private final String sqlState;

        SessionException(String session, String message, String sqlState, Throwable cause) {
            super("[%s] %s".formatted(session, message), cause);
            this.sqlState = sqlState;
        }

        static SessionException of(String session, Throwable cause) {
            String state = cause instanceof SQLException sql ? sql.getSQLState() : null;
            return new SessionException(session,
                    "%s (SQLSTATE %s)".formatted(cause.getMessage(), state), state, cause);
        }

        public String sqlState() {
            return sqlState;
        }
    }
}
