-- Dates were stored zone-less; the existing ones are taken to be UTC.
ALTER TABLE expense
    ALTER COLUMN date TYPE TIMESTAMPTZ USING date AT TIME ZONE 'UTC';

ALTER TABLE personal_expense
    ALTER COLUMN date TYPE TIMESTAMPTZ USING date AT TIME ZONE 'UTC';
