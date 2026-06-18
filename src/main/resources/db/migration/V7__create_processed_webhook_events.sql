-- V7__create_processed_webhook_events.sql
CREATE TABLE processed_webhook_events (
    event_id       VARCHAR(255) PRIMARY KEY,
    processed_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);