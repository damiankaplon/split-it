CREATE TABLE personal_expense_tag
(
    id       UUID NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name     TEXT NOT NULL
);

CREATE UNIQUE INDEX uq_personal_expense_tag_owner_name ON personal_expense_tag (owner_id, lower(name));

CREATE TABLE personal_expense
(
    id       UUID       NOT NULL PRIMARY KEY,
    owner_id TEXT       NOT NULL,
    title    TEXT       NOT NULL,
    date     TIMESTAMP  NOT NULL,
    amount   INTEGER    NOT NULL,
    currency VARCHAR(5) NOT NULL,
    tag_id   UUID       REFERENCES personal_expense_tag (id) ON DELETE SET NULL,
    version  BIGINT     NOT NULL DEFAULT 0
);

-- Serves the paged list ordered by (date desc, id desc)
CREATE INDEX idx_personal_expense_owner_date_id ON personal_expense (owner_id, date DESC, id DESC);
