CREATE TABLE settlement
(
    id          UUID                     NOT NULL PRIMARY KEY,
    project_id  UUID                     NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    debtor_id   TEXT                     NOT NULL,
    creditor_id TEXT                     NOT NULL,
    amount      INTEGER                  NOT NULL CHECK (amount > 0),
    status      TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,
    version     BIGINT                   NOT NULL DEFAULT 0
);

-- A debtor waits for one confirmation per creditor at a time
CREATE UNIQUE INDEX uq_settlement_pending ON settlement (project_id, debtor_id, creditor_id) WHERE status = 'PENDING';
CREATE INDEX idx_settlement_project_status ON settlement (project_id, status);
