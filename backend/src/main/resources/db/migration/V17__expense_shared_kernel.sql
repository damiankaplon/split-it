-- Project expenses carry their currency, like personal ones; until now it was always the project's.
ALTER TABLE expense
    ADD COLUMN currency VARCHAR(5);
UPDATE expense e
SET currency = p.currency
FROM project p
WHERE p.id = e.project_id;
ALTER TABLE expense
    ALTER COLUMN currency SET NOT NULL;

-- Project tags are keyed by (project_id, name); expenses reference them by name within their project.
ALTER TABLE expense
    ADD COLUMN tag_name TEXT;
UPDATE expense e
SET tag_name = t.name
FROM expense_tag t
WHERE t.id = e.tag_id;
ALTER TABLE expense
    DROP
        COLUMN tag_id;

ALTER TABLE expense_tag
    DROP
        COLUMN id;
ALTER TABLE expense_tag
    ADD PRIMARY KEY (project_id, name);

ALTER TABLE expense
    ADD CONSTRAINT fk_expense_tag FOREIGN KEY (project_id, tag_name) REFERENCES expense_tag (project_id, name);

-- Personal tags are keyed by (owner_id, name); personal expenses reference them by name within their owner.
ALTER TABLE personal_expense
    DROP CONSTRAINT ck_personal_expense_kind;
ALTER TABLE personal_expense
    ADD COLUMN tag_name TEXT;
UPDATE personal_expense pe
SET tag_name = t.name
FROM personal_expense_tag t
WHERE t.id = pe.tag_id;
ALTER TABLE personal_expense
    DROP
        COLUMN tag_id;

ALTER TABLE personal_expense_tag
    DROP
        COLUMN id;
ALTER TABLE personal_expense_tag
    ADD PRIMARY KEY (owner_id, name);

-- A share no longer reads title, date and tag through its project expense but holds its own copy,
-- kept in sync by events; the expense is referenced by id only.
ALTER TABLE personal_expense
    DROP CONSTRAINT personal_expense_expense_id_fkey;
ALTER TABLE personal_expense
    RENAME COLUMN expense_id TO project_expense_id;
ALTER TABLE personal_expense
    ADD COLUMN project_id UUID;

UPDATE personal_expense pe
SET title      = e.title,
    date       = e.date,
    project_id = e.project_id
FROM expense e
WHERE e.id = pe.project_expense_id;

ALTER TABLE personal_expense
    ALTER COLUMN title SET NOT NULL,
    ALTER
        COLUMN date SET NOT NULL;

-- A share is tagged with its owner's tag of the project tag's name (case-insensitive), so same-named
-- tags of different projects and of manual expenses are one personal tag; missing ones are created
-- in the project tag's color.
INSERT INTO personal_expense_tag (owner_id, name, color)
SELECT DISTINCT
    ON (pe.owner_id, lower(t.name)) pe.owner_id,
                                    t.name,
                                    t.color
FROM personal_expense pe
         JOIN expense e
              ON e.id = pe.project_expense_id
         JOIN expense_tag t ON t.project_id = e.project_id AND t.name = e.tag_name
WHERE NOT EXISTS (SELECT 1
                  FROM personal_expense_tag pt
                  WHERE pt.owner_id = pe.owner_id
                    AND lower(pt.name) = lower(t.name))
ORDER BY pe.owner_id, lower(t.name), t.name;

UPDATE personal_expense pe
SET tag_name = pt.name
FROM expense e,
     personal_expense_tag pt
WHERE e.id = pe.project_expense_id
  AND pt.owner_id = pe.owner_id
  AND lower(pt.name) = lower(e.tag_name);

ALTER TABLE personal_expense
    ADD CONSTRAINT fk_personal_expense_tag FOREIGN KEY (owner_id, tag_name)
        REFERENCES personal_expense_tag (owner_id, name);

ALTER TABLE personal_expense
    ADD CONSTRAINT ck_personal_expense_kind CHECK (
        (kind = 'MANUAL' AND project_expense_id IS NULL AND project_id IS NULL)
            OR (kind = 'PROJECT' AND project_expense_id IS NOT NULL AND project_id IS NOT NULL)
        );
