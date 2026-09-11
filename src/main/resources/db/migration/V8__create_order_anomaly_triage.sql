CREATE TABLE order_anomaly_triage (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT        NOT NULL UNIQUE REFERENCES orders(id),
    order_number    VARCHAR(50)   NOT NULL,
    classification  VARCHAR(30)   NOT NULL,
    confidence      NUMERIC(5, 4),
    rationale       VARCHAR(1000),
    model_id        VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_anomaly_triage_classification ON order_anomaly_triage(classification);
