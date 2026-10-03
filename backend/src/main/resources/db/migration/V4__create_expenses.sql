CREATE TABLE expense_tag
(
    id         UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       TEXT NOT NULL
);

CREATE UNIQUE INDEX uq_expense_tag_project_name ON expense_tag (project_id, lower(name));

CREATE TABLE expense
(
    id         UUID      NOT NULL PRIMARY KEY,
    project_id UUID      NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    title      TEXT      NOT NULL,
    date       TIMESTAMP NOT NULL,
    amount     INTEGER   NOT NULL,
    tag_id     UUID REFERENCES expense_tag (id) ON DELETE SET NULL,
    created_by TEXT      NOT NULL
);

-- Serves the paged list ordered by (date desc, id desc)
CREATE INDEX idx_expense_project_date_id ON expense (project_id, date DESC, id DESC);
