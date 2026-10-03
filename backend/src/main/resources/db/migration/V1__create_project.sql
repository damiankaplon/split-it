CREATE TABLE project
(
    id       UUID         NOT NULL PRIMARY KEY,
    owner_id UUID         NOT NULL,
    name     VARCHAR(255) NOT NULL
);

CREATE INDEX idx_project_owner_id ON project (owner_id);
