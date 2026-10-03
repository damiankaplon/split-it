-- Projects created before currencies existed were all priced in złoty
ALTER TABLE project
    ADD COLUMN currency VARCHAR(5) NOT NULL DEFAULT 'PLN';
ALTER TABLE project
    ALTER COLUMN currency DROP DEFAULT;
