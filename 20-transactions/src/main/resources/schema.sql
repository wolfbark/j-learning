DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS account;

CREATE TABLE account (
    id       BIGINT       PRIMARY KEY,
    customer VARCHAR(64)  NOT NULL,
    kind     VARCHAR(16)  NOT NULL,
    balance  BIGINT       NOT NULL
);

CREATE INDEX idx_account_customer ON account (customer);

-- Step 7's subject: entries written here are supposed to survive the rollback
-- of the business transaction that produced them.
CREATE TABLE audit_log (
    id      BIGSERIAL   PRIMARY KEY,
    message VARCHAR(256) NOT NULL
);
