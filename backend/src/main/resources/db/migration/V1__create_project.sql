CREATE TABLE project
(
    id       UUID NOT NULL PRIMARY KEY,
    owner_id TEXT NOT NULL,
    name     TEXT NOT NULL
);

CREATE INDEX idx_project_owner_id ON project (owner_id);
