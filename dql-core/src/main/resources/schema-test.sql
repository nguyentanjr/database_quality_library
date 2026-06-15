CREATE TABLE accounts (
                          id BIGINT PRIMARY KEY,
                          email VARCHAR(255) NOT NULL,
                          balance NUMERIC(19,4) NOT NULL
);

CREATE TABLE transactions (
                              id BIGINT PRIMARY KEY,
                              account_id BIGINT REFERENCES accounts(id),
                              amount DOUBLE PRECISION NOT NULL,
                              created_at VARCHAR(50)
);

CREATE INDEX idx_transactions_account_id
    ON transactions(account_id);

CREATE TABLE transaction_log (
                                 txn_id BIGINT,
                                 note TEXT
);