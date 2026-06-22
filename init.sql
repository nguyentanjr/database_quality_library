-- ============================================================
-- DQL demo fixture — schema cố ý chứa anti-pattern để verify rule
-- Mỗi block ghi rõ nó seed cho rule nào.
-- Sau khi sửa file: docker compose down -v && docker compose up
-- (init.sql chỉ chạy khi volume trống)
-- ============================================================

-- accounts: bảng "đúng" làm nền sạch (rule KHÔNG được bắt nhầm bảng này)
CREATE TABLE accounts (
                          id BIGINT PRIMARY KEY,            -- có PK
                          email VARCHAR(255) NOT NULL,
                          balance NUMERIC(19,4) NOT NULL    -- tiền đúng kiểu (numeric) — R02 không được bắt
);

-- transactions: seed nhiều anti-pattern
CREATE TABLE transactions (
                              id BIGINT PRIMARY KEY,
                              account_id BIGINT REFERENCES accounts(id), -- FK + nullable: R03 (nullable-FK) bắt
                              amount DOUBLE PRECISION NOT NULL,          -- R02: tiền lưu float
                              created_at VARCHAR(50)                     -- R02: thời gian lưu varchar
);

-- Index trên FK account_id:
--   R06 (unindexed-FK) phải IM vì FK này đã được index.
--   (Bỏ dòng này đi thì R06 phải BÁO — dùng để verify cả hai chiều của R06.)
CREATE INDEX idx_transactions_account_id ON transactions(account_id);

-- Index "mồi" cho R08 (unused-index):
--   email được index nhưng KHÔNG query nào filter email
--   => R08 phải BÁO index này unused.
--   (non-PK, non-unique nên không bị R08 skip.)
CREATE INDEX idx_accounts_email ON accounts(email);

-- transaction_log: seed missing PK
CREATE TABLE transaction_log (   -- R01: không có PRIMARY KEY
                                 txn_id BIGINT,
                                 note TEXT
);

INSERT INTO accounts (id, email, balance)
VALUES
    (1, 'alice@example.com', 1000.00),
    (2, 'bob@example.com', 2500.50),
    (3, 'charlie@example.com', 750.25);

INSERT INTO transactions (id, account_id, amount, created_at)
VALUES
    (1, 1, 120.50, '2026-06-20 10:00:00'),
    (2, 1, 75.25,  '2026-06-21 14:30:00'),
    (3, 2, 500.00, '2026-06-22 09:15:00'),

    -- account_id nullable để verify R03
    (4, NULL, 99.99, '2026-06-22 12:00:00');

INSERT INTO transaction_log (txn_id, note)
VALUES
    (1, 'payment created'),
    (2, 'payment completed'),
    (3, 'manual adjustment');