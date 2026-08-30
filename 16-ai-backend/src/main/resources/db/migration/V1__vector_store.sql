-- The pgvector table Spring AI's PgVectorStore expects, created by us rather than by
-- initialize-schema=true. Owning this DDL is the boring, correct choice: the vector table is
-- production data with an index strategy and a dimension count you will want to migrate one day.
--
-- Column names and types are dictated by PgVectorStore (id/content/metadata/embedding, id as uuid
-- because the default PgIdType is UUID). The dimension must equal
-- spring.ai.vectorstore.pgvector.dimensions and HashingEmbeddingModel.DIMENSIONS.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1024)
);

-- HNSW with cosine ops, matching PgVectorStore's default distance type (embedding <=> query).
-- An approximate index means recall is a tuning parameter, not a guarantee — one more reason the
-- eval harness in step 6 exists.
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);
