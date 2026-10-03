CREATE TABLE project_member
(
    id         UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    user_id    TEXT NOT NULL,
    username   TEXT NOT NULL,
    CONSTRAINT uq_project_member UNIQUE (project_id, user_id)
);

CREATE INDEX idx_project_member_user_id ON project_member (user_id);
