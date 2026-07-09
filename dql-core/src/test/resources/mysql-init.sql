-- Fixture MySQL nhỏ để verify SchemaCollector.resolveScope (catalog-mode) trên MySQL.
-- accounts: có PK. transactions: PK + FK(account_id) + index. transaction_log: KHÔNG PK.
CREATE TABLE accounts (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE transactions (
    id BIGINT PRIMARY KEY,
    account_id BIGINT,
    amount DOUBLE,
    CONSTRAINT fk_tx_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB;

CREATE INDEX idx_transactions_account_id ON transactions(account_id);

CREATE TABLE transaction_log (
    txn_id BIGINT,
    note TEXT
) ENGINE=InnoDB;
