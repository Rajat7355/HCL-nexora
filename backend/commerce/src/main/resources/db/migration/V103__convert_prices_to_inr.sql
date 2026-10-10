-- Convert the original USD-priced catalog and order history using the 2026-10-10
-- USD/INR reference rate of 96.867192 INR per USD.
UPDATE products
SET price = ROUND(price * 96.867192, 2),
    currency = 'INR'
WHERE currency = 'USD';

ALTER TABLE products ALTER COLUMN currency SET DEFAULT 'INR';

UPDATE order_items oi
SET unit_price = ROUND(oi.unit_price * 96.867192, 2)
FROM orders o
WHERE oi.order_id = o.id AND o.currency = 'USD';

WITH converted_orders AS (
    SELECT o.id,
           ROUND(COALESCE(SUM(oi.line_total), 0), 2) AS subtotal,
           ROUND(o.shipping_fee * 96.867192, 2) AS shipping_fee,
           ROUND(o.discount * 96.867192, 2) AS discount
    FROM orders o
    LEFT JOIN order_items oi ON oi.order_id = o.id
    WHERE o.currency = 'USD'
    GROUP BY o.id, o.shipping_fee, o.discount
)
UPDATE orders o
SET subtotal = c.subtotal,
    shipping_fee = c.shipping_fee,
    discount = c.discount,
    total = c.subtotal + c.shipping_fee - c.discount,
    currency = 'INR'
FROM converted_orders c
WHERE o.id = c.id;

ALTER TABLE orders ALTER COLUMN currency SET DEFAULT 'INR';

UPDATE customer_preferences
SET budget_min = CASE WHEN budget_min IS NULL THEN NULL ELSE ROUND(budget_min * 96.867192, 2) END,
    budget_max = CASE WHEN budget_max IS NULL THEN NULL ELSE ROUND(budget_max * 96.867192, 2) END,
    preferred_currency = 'INR'
WHERE preferred_currency = 'USD';

ALTER TABLE customer_preferences ALTER COLUMN preferred_currency SET DEFAULT 'INR';
