package dev.vlearning.lending;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one service that does everything: state changes AND the dashboard query.
 * It works, the tests pin it, and it is the subject of this lesson — step 1
 * takes it apart along the command/query seam.
 */
@Service
public class LibraryService {

    static final int LOAN_PERIOD_DAYS = 21;
    static final int MAX_OPEN_LOANS = 5;

    private final JdbcClient jdbc;
    private final Clock clock;

    LibraryService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // State changes (the future command side)
    // ------------------------------------------------------------------

    @Transactional
    public LoanReceipt borrowBook(long memberId, long bookId) {
        long memberCount = jdbc.sql("SELECT count(*) FROM members WHERE id = :id")
                .param("id", memberId).query(Long.class).single();
        if (memberCount == 0) {
            throw new NoSuchElementException("No member with id " + memberId);
        }
        int copies = jdbc.sql("SELECT copies FROM books WHERE id = :id")
                .param("id", bookId).query(Integer.class).optional()
                .orElseThrow(() -> new NoSuchElementException("No book with id " + bookId));
        long openForBook = jdbc.sql("SELECT count(*) FROM loans WHERE book_id = :id AND returned_on IS NULL")
                .param("id", bookId).query(Long.class).single();
        if (openForBook >= copies) {
            throw new IllegalStateException("All copies of book %d are on loan".formatted(bookId));
        }
        long openForMember = jdbc.sql("SELECT count(*) FROM loans WHERE member_id = :id AND returned_on IS NULL")
                .param("id", memberId).query(Long.class).single();
        if (openForMember >= MAX_OPEN_LOANS) {
            throw new IllegalStateException(
                    "Member %d has reached the limit of %d open loans".formatted(memberId, MAX_OPEN_LOANS));
        }

        LocalDate borrowedOn = LocalDate.now(clock);
        LocalDate dueOn = borrowedOn.plusDays(LOAN_PERIOD_DAYS);
        long loanId = jdbc.sql("""
                        INSERT INTO loans (member_id, book_id, borrowed_on, due_on)
                        VALUES (:memberId, :bookId, :borrowedOn, :dueOn)
                        RETURNING id
                        """)
                .param("memberId", memberId).param("bookId", bookId)
                .param("borrowedOn", borrowedOn).param("dueOn", dueOn)
                .query(Long.class).single();
        return new LoanReceipt(loanId, dueOn);
    }

    @Transactional
    public ReturnReceipt returnBook(long loanId) {
        LoanRow loan = jdbc.sql("SELECT id, member_id, book_id, due_on, returned_on FROM loans WHERE id = :id")
                .param("id", loanId)
                .query((rs, rowNum) -> new LoanRow(
                        rs.getLong("id"),
                        rs.getLong("member_id"),
                        rs.getLong("book_id"),
                        rs.getObject("due_on", LocalDate.class),
                        rs.getObject("returned_on", LocalDate.class)))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("No loan with id " + loanId));
        if (loan.returnedOn() != null) {
            throw new IllegalStateException("Loan %d was already returned on %s".formatted(loanId, loan.returnedOn()));
        }

        LocalDate returnedOn = LocalDate.now(clock);
        jdbc.sql("UPDATE loans SET returned_on = :returnedOn WHERE id = :id")
                .param("returnedOn", returnedOn).param("id", loanId)
                .update();
        return new ReturnReceipt(loanId, returnedOn, returnedOn.isAfter(loan.dueOn()));
    }

    // ------------------------------------------------------------------
    // The dashboard query (the future query side) — read it slowly.
    // One request = one three-table join returning a row per loan, then a
    // pile of per-request aggregation in Java. Correct, pinned by tests,
    // and doing work at read time that never changes between reads.
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public MemberActivity getMemberActivity(long memberId) {
        List<ActivityRow> rows = jdbc.sql("""
                        SELECT m.name, m.email,
                               l.id AS loan_id, l.borrowed_on, l.due_on, l.returned_on,
                               b.id AS book_id, b.title, b.author
                        FROM members m
                        LEFT JOIN loans l ON l.member_id = m.id
                        LEFT JOIN books b ON b.id = l.book_id
                        WHERE m.id = :memberId
                        ORDER BY l.borrowed_on, l.id
                        """)
                .param("memberId", memberId)
                .query((rs, rowNum) -> new ActivityRow(
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getObject("loan_id", Long.class),
                        rs.getObject("borrowed_on", LocalDate.class),
                        rs.getObject("due_on", LocalDate.class),
                        rs.getObject("returned_on", LocalDate.class),
                        rs.getObject("book_id", Long.class),
                        rs.getString("title"),
                        rs.getString("author")))
                .list();
        if (rows.isEmpty()) {
            throw new NoSuchElementException("No member with id " + memberId);
        }

        LocalDate today = LocalDate.now(clock);
        String name = rows.getFirst().name();
        String email = rows.getFirst().email();

        int totalLoans = 0;
        int openLoans = 0;
        int returnedLoans = 0;
        int lateReturns = 0;
        Set<Long> bookIds = new HashSet<>();
        Set<String> authors = new HashSet<>();
        Map<String, Integer> loansPerAuthor = new HashMap<>();
        LocalDate lastActivityOn = null;
        List<MemberActivity.OpenLoan> currentLoans = new ArrayList<>();

        for (ActivityRow row : rows) {
            if (row.loanId() == null) {
                continue; // LEFT JOIN artifact: the member exists but has no loans
            }
            totalLoans++;
            bookIds.add(row.bookId());
            authors.add(row.author());
            loansPerAuthor.merge(row.author(), 1, Integer::sum);
            if (lastActivityOn == null || row.borrowedOn().isAfter(lastActivityOn)) {
                lastActivityOn = row.borrowedOn();
            }
            if (row.returnedOn() != null) {
                returnedLoans++;
                if (row.returnedOn().isAfter(row.dueOn())) {
                    lateReturns++;
                }
                if (row.returnedOn().isAfter(lastActivityOn)) {
                    lastActivityOn = row.returnedOn();
                }
            } else {
                openLoans++;
                currentLoans.add(new MemberActivity.OpenLoan(
                        row.loanId(), row.title(), row.dueOn(), row.dueOn().isBefore(today)));
            }
        }

        String favoriteAuthor = null;
        int favoriteCount = 0;
        for (Map.Entry<String, Integer> entry : new TreeMap<>(loansPerAuthor).entrySet()) {
            if (entry.getValue() > favoriteCount) {
                favoriteCount = entry.getValue();
                favoriteAuthor = entry.getKey();
            }
        }

        currentLoans.sort(Comparator.comparing(MemberActivity.OpenLoan::dueOn)
                .thenComparing(MemberActivity.OpenLoan::loanId));

        return new MemberActivity(memberId, name, email,
                totalLoans, openLoans, returnedLoans, lateReturns,
                bookIds.size(), authors.size(),
                favoriteAuthor, lastActivityOn, List.copyOf(currentLoans));
    }

    public record LoanReceipt(long loanId, LocalDate dueOn) {
    }

    public record ReturnReceipt(long loanId, LocalDate returnedOn, boolean late) {
    }

    private record LoanRow(long id, long memberId, long bookId, LocalDate dueOn, LocalDate returnedOn) {
    }

    private record ActivityRow(String name, String email, Long loanId, LocalDate borrowedOn,
                               LocalDate dueOn, LocalDate returnedOn, Long bookId, String title, String author) {
    }
}
