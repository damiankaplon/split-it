CREATE TABLE saldo
(
    project_id UUID   NOT NULL PRIMARY KEY,
    version    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE saldo_member
(
    project_id            UUID   NOT NULL REFERENCES saldo (project_id),
    user_id               TEXT   NOT NULL,
    total_expenses_amount BIGINT NOT NULL,
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE saldo_debt
(
    project_id  UUID    NOT NULL REFERENCES saldo (project_id),
    debtor_id   TEXT    NOT NULL,
    creditor_id TEXT    NOT NULL,
    amount      INTEGER NOT NULL,
    PRIMARY KEY (project_id, debtor_id, creditor_id)
);
