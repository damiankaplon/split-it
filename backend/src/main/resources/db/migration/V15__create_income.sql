CREATE TABLE income
(
    id         UUID        NOT NULL PRIMARY KEY,
    owner_id   TEXT        NOT NULL,
    title      TEXT        NOT NULL,
    date       TIMESTAMPTZ NOT NULL,
    delimiting BOOLEAN     NOT NULL,
    amount     INTEGER     NOT NULL,
    currency   VARCHAR(5)  NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_income_owner_date ON income (owner_id, date DESC);
