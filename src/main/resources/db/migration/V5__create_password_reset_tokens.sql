CREATE TABLE password_reset_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_prt_token      ON password_reset_tokens(token);
CREATE INDEX idx_prt_customer   ON password_reset_tokens(customer_id);
