CREATE TABLE orders (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID          NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    total_amount  DECIMAL(19,2) NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);

CREATE TABLE order_items (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    quantity     INT           NOT NULL CHECK (quantity >= 1 AND quantity <= 100),
    unit_price   DECIMAL(19,2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
