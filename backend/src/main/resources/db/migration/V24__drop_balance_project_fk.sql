-- The balance belongs to the balancing module and the project to the project module; modules
-- refer to each other's data by id only, as personal_expense.project_expense_id does since V17.
-- A balance is created from the project's ProjectCreated event.
ALTER TABLE balance
    DROP CONSTRAINT balance_project_id_fkey;
