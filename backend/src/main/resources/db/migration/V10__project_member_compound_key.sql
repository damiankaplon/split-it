ALTER TABLE project_member DROP CONSTRAINT uq_project_member;
ALTER TABLE project_member DROP COLUMN id;
ALTER TABLE project_member
    ADD PRIMARY KEY (project_id, user_id);
