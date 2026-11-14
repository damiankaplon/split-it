-- Spring Modulith's Event Publication Registry (JDBC, schema v2 for PostgreSQL).
-- One row per event and listener, written in the publisher's transaction; a row without a
-- completion date is an event that listener has not handled yet and is republished on restart.
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- Events the balance has already applied. Events are delivered at least once, so a
-- republished one must not be applied twice.
CREATE TABLE balance_applied_event
(
    event_id   UUID                     NOT NULL PRIMARY KEY,
    project_id UUID                     NOT NULL REFERENCES balance (project_id),
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL
);
