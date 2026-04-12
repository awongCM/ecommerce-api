CREATE TABLE customers (
    id             BIGSERIAL PRIMARY KEY,
    first_name     VARCHAR(100)        NOT NULL,
    last_name      VARCHAR(100)        NOT NULL,
    email          VARCHAR(255) UNIQUE NOT NULL,
    password_hash  VARCHAR(255)        NOT NULL,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP
);

CREATE TABLE customer_roles (
    customer_id BIGINT      NOT NULL REFERENCES customers(id),
    roles       VARCHAR(50) NOT NULL,
    PRIMARY KEY (customer_id, roles)
);

CREATE TABLE addresses (
    id            BIGSERIAL PRIMARY KEY,
    customer_id   BIGINT       NOT NULL REFERENCES customers(id),
    street_line1  VARCHAR(255) NOT NULL,
    street_line2  VARCHAR(255),
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100) NOT NULL,
    postcode      VARCHAR(20)  NOT NULL,
    country       VARCHAR(100) NOT NULL,
    is_default    BOOLEAN      DEFAULT FALSE
);

CREATE TABLE carts (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL UNIQUE REFERENCES customers(id)
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT  NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id BIGINT  NOT NULL REFERENCES products(id),
    quantity   INTEGER NOT NULL CHECK (quantity > 0)
);
