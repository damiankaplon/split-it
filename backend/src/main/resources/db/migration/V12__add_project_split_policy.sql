-- Projects created before split policies existed split everything equally
ALTER TABLE project
    ADD COLUMN split_policy VARCHAR(32) NOT NULL DEFAULT 'EQUAL_SHARE';
ALTER TABLE project
    ALTER COLUMN split_policy DROP DEFAULT;
