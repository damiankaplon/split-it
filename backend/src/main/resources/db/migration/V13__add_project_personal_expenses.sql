-- Personal expenses are either entered manually or are a member's share of a project expense,
-- which sources its title, date and tag from the expense.
ALTER TABLE personal_expense
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE personal_expense
    ALTER COLUMN kind DROP DEFAULT;

ALTER TABLE personal_expense
    ADD COLUMN expense_id UUID REFERENCES expense (id) ON DELETE CASCADE;

ALTER TABLE personal_expense
    ALTER COLUMN title DROP NOT NULL,
    ALTER
        COLUMN date DROP
        NOT NULL;

ALTER TABLE personal_expense
    ADD CONSTRAINT ck_personal_expense_kind CHECK (
        (kind = 'MANUAL' AND title IS NOT NULL AND date IS NOT NULL AND expense_id IS NULL)
            OR (kind = 'PROJECT' AND expense_id IS NOT NULL AND title IS NULL AND date IS NULL AND tag_id IS NULL)
        );

-- One share per member of each project expense; also serves lookups by expense
CREATE UNIQUE INDEX uq_personal_expense_expense_owner ON personal_expense (expense_id, owner_id);
