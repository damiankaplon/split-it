CREATE TABLE balance
(
    project_id UUID   NOT NULL PRIMARY KEY REFERENCES project (id),
    version    BIGINT NOT NULL DEFAULT 0
);

-- One signed amount per user in minor units: positive means the group owes them, negative means they owe the group.
-- A user whose amount is 0 has no row.
CREATE TABLE balance_entry
(
    project_id UUID   NOT NULL REFERENCES balance (project_id),
    user_id    TEXT   NOT NULL,
    amount     BIGINT NOT NULL,
    PRIMARY KEY (project_id, user_id)
);
