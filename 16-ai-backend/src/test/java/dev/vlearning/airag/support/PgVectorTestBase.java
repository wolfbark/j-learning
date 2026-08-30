package dev.vlearning.airag.support;

import dev.vlearning.airag.ingest.IngestionService;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One pgvector container for the whole test run (singleton pattern: static start, reaped by Ryuk).
 * Testcontainers 2.x: the module is {@code testcontainers-postgresql}, the class lives in
 * {@code org.testcontainers.postgresql}, and the self-typed generic is gone.
 *
 * <p>pgvector/pgvector:pg17 is Postgres 17 with the {@code vector} extension already installed, so
 * it has to be declared as a substitute for the image the Postgres module expects.
 */
@ActiveProfiles("test")
public abstract class PgVectorTestBase {

    protected static final PostgreSQLContainer PGVECTOR = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        PGVECTOR.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PGVECTOR::getJdbcUrl);
        registry.add("spring.datasource.username", PGVECTOR::getUsername);
        registry.add("spring.datasource.password", PGVECTOR::getPassword);
    }

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected IngestionService ingestionService;

    @Autowired
    protected ScriptedChatModel scriptedChatModel;

    /** The store is shared by every test class, so each test starts from an empty index. */
    @BeforeEach
    void resetIndexAndModel() {
        this.jdbc.sql("TRUNCATE vector_store").update();
        this.scriptedChatModel.reset();
    }

    protected long storedVectorCount() {
        return this.jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single();
    }
}
