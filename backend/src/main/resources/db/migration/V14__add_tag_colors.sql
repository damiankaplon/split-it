-- Existing tags get a random color from the same palette new tags use.
ALTER TABLE expense_tag
    ADD COLUMN color TEXT NOT NULL DEFAULT '#6366f1';
ALTER TABLE personal_expense_tag
    ADD COLUMN color TEXT NOT NULL DEFAULT '#6366f1';

UPDATE expense_tag
SET color = (ARRAY ['#ef4444', '#f97316', '#f59e0b', '#84cc16', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6', '#6366f1',
    '#8b5cf6', '#d946ef', '#ec4899'])[1 + floor(random() * 12)::int];
UPDATE personal_expense_tag
SET color = (ARRAY ['#ef4444', '#f97316', '#f59e0b', '#84cc16', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6', '#6366f1',
    '#8b5cf6', '#d946ef', '#ec4899'])[1 + floor(random() * 12)::int];

ALTER TABLE expense_tag
    ALTER COLUMN color DROP DEFAULT;
ALTER TABLE personal_expense_tag
    ALTER COLUMN color DROP DEFAULT;
