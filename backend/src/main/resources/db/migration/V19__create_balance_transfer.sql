-- The transfers that bring every amount of a balance to 0, brought up to date by every change of the balance.
-- At most one per pair of users: the greedy algorithm never pairs the same two users twice.
CREATE TABLE balance_transfer
(
    project_id   UUID   NOT NULL REFERENCES balance (project_id),
    from_user_id TEXT   NOT NULL,
    to_user_id   TEXT   NOT NULL,
    amount       BIGINT NOT NULL CHECK (amount > 0),
    PRIMARY KEY (project_id, from_user_id, to_user_id),
    CHECK (from_user_id <> to_user_id)
);
