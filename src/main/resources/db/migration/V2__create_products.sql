CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    parent_id   BIGINT REFERENCES categories(id)
);

CREATE TABLE products (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255)       NOT NULL,
    description    TEXT,
    price          NUMERIC(10,2)      NOT NULL CHECK (price >= 0),
    stock_quantity INTEGER            NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    category_id    BIGINT             REFERENCES categories(id),
    sku            VARCHAR(100) UNIQUE NOT NULL,
    active         BOOLEAN            DEFAULT TRUE,
    created_by     VARCHAR(255),
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    version        BIGINT             DEFAULT 0
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active   ON products(active);
CREATE INDEX idx_products_sku      ON products(sku);
