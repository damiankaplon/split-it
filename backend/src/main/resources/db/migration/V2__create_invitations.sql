CREATE TABLE project_invitation
(
    id          UUID                     NOT NULL PRIMARY KEY,
    project_id  UUID                     NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    token       TEXT                     NOT NULL UNIQUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_by TEXT
);

CREATE INDEX idx_project_invitation_project_id ON project_invitation (project_id);
