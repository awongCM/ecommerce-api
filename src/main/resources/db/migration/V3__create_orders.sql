CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,
    order_number        VARCHAR(50) UNIQUE NOT NULL,
    customer_id         BIGINT             NOT NULL REFERENCES customers(id),
    status              VARCHAR(30)        NOT NULL DEFAULT 'PENDING',
    total_amount        NUMERIC(12,2)      NOT NULL,
    shipping_address_id BIGINT             REFERENCES addresses(id),
    idempotency_key     VARCHAR(255) UNIQUE,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    version             BIGINT             DEFAULT 0
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id BIGINT        NOT NULL REFERENCES products(id),
    quantity   INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10,2) NOT NULL
);

CREATE TABLE payments (
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT        NOT NULL REFERENCES orders(id),
    amount            NUMERIC(12,2) NOT NULL,
    status            VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    idempotency_key   VARCHAR(255) UNIQUE NOT NULL,
    gateway_reference VARCHAR(255),
    card_last4        VARCHAR(4),
    created_at        TIMESTAMP,
    processed_at      TIMESTAMP
);

CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_status   ON orders(status);
CREATE INDEX idx_orders_idem_key ON orders(idempotency_key);
