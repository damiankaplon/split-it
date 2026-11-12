-- Expenses imported straight into the database (version 5 UUIDs; the application only creates version 4 ones) were
-- never shared: saldo never counted them, so they were their payer's alone. V20 split them among the project's
-- members all the same, which charged members for expenses from before they even joined. They go back to their
-- payer, and every balance is built again (as V21 does) from the corrected splits.
DELETE
FROM expense_split s
    USING expense e
WHERE e.id = s.expense_id
  AND substring(e.id::TEXT, 15, 1) = '5';

INSERT INTO expense_split (expense_id, user_id, amount)
SELECT id, created_by, amount
FROM expense
WHERE substring(id::TEXT, 15, 1) = '5';

-- Balances nothing has changed since V21: only those can still be compared with saldo below, which stopped
-- learning about expenses and settlements when balance took over.
CREATE TEMPORARY TABLE unchanged ON COMMIT DROP AS
SELECT project_id
FROM balance
WHERE version = 0;

DELETE
FROM balance_transfer;
DELETE
FROM balance_entry;
DELETE
FROM balance;

-- From here on the same as V21.
INSERT INTO balance (project_id)
SELECT id
FROM project;

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

-- Every unchanged balance must agree with saldo, which shared every expense it counted equally between all its
-- members: a member's amount there is paid + settled - total / members. Balance splits every expense on its own,
-- so it may differ by less than one minor unit per expense whose amount didn't divide evenly; anything more means
-- the splits above are still wrong, and nothing is changed.
DO
$$
    DECLARE
        broken TEXT;
    BEGIN
        -- Amounts are compared multiplied by the members count, so that saldo's stay whole
        WITH saldo_project AS (SELECT project_id, count(*) AS members, sum(total_expenses_amount) AS total
                               FROM saldo_member
                               WHERE project_id IN (SELECT project_id FROM unchanged)
                               GROUP BY project_id),
             users AS (SELECT project_id, user_id
                       FROM saldo_member
                       UNION
                       SELECT project_id, user_id
                       FROM balance_entry),
             compared AS (SELECT p.project_id,
                                 p.members,
                                 coalesce(b.amount, 0) * p.members
                                     - ((coalesce(m.total_expenses_amount, 0) + coalesce(m.settled_amount, 0)) * p.members
                                     - p.total) AS difference
                          FROM saldo_project p
                                   JOIN users u ON u.project_id = p.project_id
                                   LEFT JOIN saldo_member m ON m.project_id = u.project_id AND m.user_id = u.user_id
                                   LEFT JOIN balance_entry b ON b.project_id = u.project_id AND b.user_id = u.user_id),
             rounded AS (SELECT e.project_id, count(*) AS expenses
                         FROM expense e
                                  JOIN (SELECT expense_id, count(*) AS splits
                                        FROM expense_split
                                        GROUP BY expense_id) s ON s.expense_id = e.id
                         WHERE s.splits > 1
                           AND e.amount % s.splits <> 0
                         GROUP BY e.project_id)
        SELECT string_agg(DISTINCT c.project_id::TEXT, ', ')
        INTO broken
        FROM compared c
                 LEFT JOIN rounded r ON r.project_id = c.project_id
        WHERE abs(c.difference) > c.members * coalesce(r.expenses, 0);
        IF broken IS NOT NULL THEN
            RAISE EXCEPTION 'Balances differ from saldo in projects: %', broken;
        END IF;
    END
$$;

-- The transfers that settle the amounts above, as V21 calculates them.
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
