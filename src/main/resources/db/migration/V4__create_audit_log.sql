CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    VARCHAR(255) NOT NULL,
    action       VARCHAR(100) NOT NULL,
    old_value    TEXT,
    new_value    TEXT,
    performed_by VARCHAR(255) NOT NULL,
    timestamp    TIMESTAMP    NOT NULL,
    ip_address   VARCHAR(50),
    trace_id     VARCHAR(100)
);

CREATE INDEX idx_audit_entity  ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_user    ON audit_logs(performed_by);
CREATE INDEX idx_audit_time    ON audit_logs(timestamp);
