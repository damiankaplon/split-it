-- Fills balance (V18, V19) for every project from its expenses, their splits (V20) and its confirmed settlements,
-- exactly as Balance would have, had every one of them gone through it:
--   an expense:     its payer +amount, every member of its splits -their share
--   a settlement:   its debtor +amount (paid off what they owed), its creditor -amount (got what they were owed)
-- in whole minor units, so the amounts add up to 0 and later changes undo exactly what was added here.

-- A project without expenses has nothing to owe yet: an empty balance.
INSERT INTO balance (project_id)
SELECT id
FROM project;

-- A user whose amount is 0 has no row.
INSERT INTO balance_entry (project_id, user_id, amount)
SELECT project_id, user_id, sum(amount)
FROM (SELECT project_id, created_by, amount::BIGINT
      FROM expense
      UNION ALL
      SELECT e.project_id, s.user_id, -s.amount::BIGINT
      FROM expense_split s
               JOIN expense e ON e.id = s.expense_id
      UNION ALL
      SELECT project_id, debtor_id, amount::BIGINT
      FROM settlement
      WHERE status = 'CONFIRMED'
      UNION ALL
      SELECT project_id, creditor_id, -amount::BIGINT
      FROM settlement
      WHERE status = 'CONFIRMED') AS changes (project_id, user_id, amount)
GROUP BY project_id, user_id
HAVING sum(amount) <> 0;

-- The transfers that settle the amounts above, calculated like Balance does: the biggest debtor pays the
-- biggest creditor until nobody is left, ties go to the first user id (byte order, like Kotlin's String order).
CREATE TEMPORARY TABLE remaining ON COMMIT DROP AS
SELECT project_id, user_id, amount
FROM balance_entry;

DO
$$
    DECLARE
        pid      UUID;
        creditor RECORD;
        debtor   RECORD;
        settled  BIGINT;
    BEGIN
        FOR pid IN SELECT DISTINCT project_id FROM remaining
            LOOP
                LOOP
                    SELECT user_id, amount
                    INTO creditor
                    FROM remaining
                    WHERE project_id = pid
                      AND amount > 0
                    ORDER BY amount DESC, user_id COLLATE "C"
                    LIMIT 1;
                    EXIT WHEN NOT FOUND;

                    -- Not aliased as amount: ORDER BY would then sort by the alias instead of the column
                    SELECT user_id, -amount AS debt
                    INTO debtor
                    FROM remaining
                    WHERE project_id = pid
                      AND amount < 0
                    ORDER BY amount, user_id COLLATE "C"
                    LIMIT 1;
                    EXIT WHEN NOT FOUND;

                    settled := LEAST(creditor.amount, debtor.debt);
                    INSERT INTO balance_transfer (project_id, from_user_id, to_user_id, amount)
                    VALUES (pid, debtor.user_id, creditor.user_id, settled);
                    UPDATE remaining SET amount = amount - settled WHERE project_id = pid AND user_id = creditor.user_id;
                    UPDATE remaining SET amount = amount + settled WHERE project_id = pid AND user_id = debtor.user_id;
                END LOOP;
            END LOOP;
    END
$$;
