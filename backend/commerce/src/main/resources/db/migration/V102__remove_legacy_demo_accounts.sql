CREATE TEMP TABLE legacy_demo_user_ids ON COMMIT DROP AS
SELECT id
FROM users
WHERE email IN (
    'admin@shopsmart.test',
    'basic@shopsmart.test',
    'prime@shopsmart.test',
    'premium@shopsmart.test'
);

CREATE TEMP TABLE legacy_demo_stock_restore ON COMMIT DROP AS
SELECT m.product_id, m.warehouse_id, -SUM(m.change_qty)::INT AS quantity
FROM inventory_movements m
JOIN orders o ON o.id = m.order_id
JOIN legacy_demo_user_ids u ON u.id = o.user_id
GROUP BY m.product_id, m.warehouse_id
HAVING SUM(m.change_qty) < 0;

UPDATE inventory i
SET quantity_on_hand = i.quantity_on_hand + s.quantity,
    updated_at = now()
FROM legacy_demo_stock_restore s
WHERE i.product_id = s.product_id AND i.warehouse_id = s.warehouse_id;

DELETE FROM inventory_movements m
USING orders o, legacy_demo_user_ids u
WHERE m.order_id = o.id AND o.user_id = u.id;

DELETE FROM orders o
USING legacy_demo_user_ids u
WHERE o.user_id = u.id;

DELETE FROM users u
USING legacy_demo_user_ids d
WHERE u.id = d.id;
