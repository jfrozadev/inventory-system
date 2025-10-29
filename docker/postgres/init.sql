
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

DROP TABLE IF EXISTS inventory_events CASCADE;
DROP TABLE IF EXISTS products CASCADE;

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    store_id VARCHAR(50) NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    min_stock_level INTEGER NOT NULL DEFAULT 10,
    max_stock_level INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT products_quantity_check CHECK (quantity >= 0),
    CONSTRAINT products_reserved_check CHECK (reserved_quantity >= 0),
    CONSTRAINT products_store_product_unique UNIQUE (store_id, product_id)
);

CREATE INDEX idx_products_store_id ON products(store_id);
CREATE INDEX idx_products_product_id ON products(product_id);
CREATE INDEX idx_products_store_product ON products(store_id, product_id);
CREATE INDEX idx_products_last_updated ON products(last_updated);

CREATE TABLE inventory_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    store_id VARCHAR(50) NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    quantity_before INTEGER,
    quantity_after INTEGER,
    quantity_delta INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    metadata JSONB
);

CREATE INDEX idx_events_event_id ON inventory_events(event_id);
CREATE INDEX idx_events_store_id ON inventory_events(store_id);
CREATE INDEX idx_events_product_id ON inventory_events(product_id);
CREATE INDEX idx_events_status ON inventory_events(status);
CREATE INDEX idx_events_timestamp ON inventory_events(timestamp);


DO $$
DECLARE
    store_num INT;
    product_num INT;
BEGIN
    RAISE NOTICE 'Seeding 100,000 products...';

    FOR store_num IN 1..100 LOOP
        FOR product_num IN 1..1000 LOOP
            INSERT INTO products (
                store_id,
                product_id,
                name,
                quantity
            ) VALUES (
                'STORE_' || LPAD(store_num::TEXT, 3, '0'),
                'PROD_' || LPAD(product_num::TEXT, 4, '0'),
                'Product ' || product_num,
                FLOOR(RANDOM() * 900 + 100)::INTEGER
            )
            ON CONFLICT DO NOTHING;
        END LOOP;

        IF store_num % 10 = 0 THEN
            RAISE NOTICE 'Completed % stores', store_num;
        END IF;
    END LOOP;

    RAISE NOTICE 'Seed completed!';
END $$;


CREATE OR REPLACE FUNCTION update_last_updated_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_products_last_updated
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_last_updated_column();

ANALYZE products;
ANALYZE inventory_events;
VACUUM ANALYZE;

SELECT COUNT(*) as total_products FROM products;