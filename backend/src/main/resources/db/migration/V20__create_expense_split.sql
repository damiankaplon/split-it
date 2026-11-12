-- How an expense's amount was divided among the project members, in minor units. Kept so that a change or a
-- deletion of the expense can undo exactly what it added to the balance.
CREATE TABLE expense_split
(
    expense_id UUID    NOT NULL REFERENCES expense (id) ON DELETE CASCADE,
    user_id    TEXT    NOT NULL,
    amount     INTEGER NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (expense_id, user_id)
);

-- Existing expenses are split equally among the project's current members, as saldo divided them: every amount
-- rounded down, the minor units left over go one each to the first members by user id (byte order, like Kotlin's
-- String order), as EqualShare does. The balances (V21) are then built from these splits.
INSERT INTO expense_split (expense_id, user_id, amount)
SELECT e.id,
       m.user_id,
       e.amount / count(*) OVER w
           + CASE WHEN row_number() OVER w <= e.amount % count(*) OVER w THEN 1 ELSE 0 END
FROM expense e
         JOIN project_member m ON m.project_id = e.project_id
WINDOW w AS (PARTITION BY e.id ORDER BY m.user_id COLLATE "C"
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING);
