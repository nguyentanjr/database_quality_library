-- accounts: bảng "đúng" làm nền sạch
CREATE TABLE accounts (
  id BIGINT PRIMARY KEY,            -- có PK
  email VARCHAR(255) NOT NULL,
  balance NUMERIC(19,4) NOT NULL    -- tiền đúng kiểu
);

-- transactions: seed 2 anti-pattern
CREATE TABLE transactions (
  id BIGINT PRIMARY KEY,
  account_id BIGINT REFERENCES accounts(id),
  amount DOUBLE PRECISION NOT NULL, -- suspicious type: tiền lưu float
  created_at VARCHAR(50)            -- suspicious type: thời gian lưu varchar
);

-- transaction_log: seed missing PK
CREATE TABLE transaction_log (      -- không PRIMARY KEY
  txn_id BIGINT,
  note TEXT
);