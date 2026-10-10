-- =====================================================================
-- 23_inventory_triggers.sql
-- OWNER : Lovish - Catalog & Inventory (inventory part)  (only this member edits this file)
-- TABLES: triggers only
-- NEEDS : 11_inventory, 22_cart_orders
-- =====================================================================


-- Orders never write to inventory. Stock moves automatically:
--   inserting an order_item takes stock from the warehouse with the most available units
--   cancelling an order puts the same units back (ledger rows keep a full audit trail)

CREATE OR REPLACE FUNCTION fn_order_item_take_stock() RETURNS trigger AS $$
DECLARE
    wh    BIGINT;
    avail INT;
BEGIN
    SELECT warehouse_id, quantity_on_hand - quantity_reserved
      INTO wh, avail
      FROM inventory
     WHERE product_id = NEW.product_id
     ORDER BY quantity_on_hand - quantity_reserved DESC
     LIMIT 1
       FOR UPDATE;

    IF wh IS NULL OR avail < NEW.quantity THEN
        RAISE EXCEPTION 'INSUFFICIENT_STOCK: product % needs % units', NEW.product_id, NEW.quantity
            USING ERRCODE = 'P0001';
    END IF;

    UPDATE inventory
       SET quantity_on_hand = quantity_on_hand - NEW.quantity, updated_at = now()
     WHERE product_id = NEW.product_id AND warehouse_id = wh;

    INSERT INTO inventory_movements (product_id, warehouse_id, change_qty, reason, order_id)
    VALUES (NEW.product_id, wh, -NEW.quantity, 'SALE', NEW.order_id);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_item_take_stock
    BEFORE INSERT ON order_items
    FOR EACH ROW EXECUTE FUNCTION fn_order_item_take_stock();

CREATE OR REPLACE FUNCTION fn_order_cancel_restock() RETURNS trigger AS $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT product_id, warehouse_id, SUM(-change_qty)::INT AS qty
          FROM inventory_movements
         WHERE order_id = NEW.id AND reason = 'SALE'
         GROUP BY product_id, warehouse_id
    LOOP
        UPDATE inventory
           SET quantity_on_hand = quantity_on_hand + r.qty, updated_at = now()
         WHERE product_id = r.product_id AND warehouse_id = r.warehouse_id;

        INSERT INTO inventory_movements (product_id, warehouse_id, change_qty, reason, order_id)
        VALUES (r.product_id, r.warehouse_id, r.qty, 'RETURN', NEW.id);
    END LOOP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_order_cancel_restock
    AFTER UPDATE OF status ON orders
    FOR EACH ROW
    WHEN (NEW.status = 'CANCELLED' AND OLD.status <> 'CANCELLED')
    EXECUTE FUNCTION fn_order_cancel_restock();
