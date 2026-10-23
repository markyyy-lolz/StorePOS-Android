-- RestaurantPOS Cloud v0.1.0. Separate Neon project; no StorePOS objects touched.
BEGIN;
CREATE SCHEMA IF NOT EXISTS restaurantpos;
CREATE TABLE IF NOT EXISTS restaurantpos.restaurants (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 name text NOT NULL CHECK (char_length(name) BETWEEN 1 AND 140),
 address text NOT NULL DEFAULT '',
 phone text NOT NULL DEFAULT '',
 service_charge_percent numeric(5,2) NOT NULL DEFAULT 0 CHECK (service_charge_percent BETWEEN 0 AND 20),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS restaurantpos.staff (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 restaurant_id uuid NOT NULL REFERENCES restaurantpos.restaurants(id),
 full_name text NOT NULL,
 email text NOT NULL UNIQUE,
 password_hash text NOT NULL,
 role text NOT NULL CHECK (role IN ('owner','manager','cashier','kitchen')),
 active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS rp_staff_tenant_idx ON restaurantpos.staff(restaurant_id);
CREATE TABLE IF NOT EXISTS restaurantpos.sessions (
 token_hash text PRIMARY KEY,
 staff_id uuid NOT NULL REFERENCES restaurantpos.staff(id) ON DELETE CASCADE,
 expires_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS rp_sessions_expiry_idx ON restaurantpos.sessions(expires_at);
CREATE TABLE IF NOT EXISTS restaurantpos.categories (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 restaurant_id uuid NOT NULL REFERENCES restaurantpos.restaurants(id),
 name text NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
 sort_order integer NOT NULL DEFAULT 0,
 UNIQUE (restaurant_id, name),
 UNIQUE (restaurant_id, id)
);
CREATE TABLE IF NOT EXISTS restaurantpos.menu_items (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 restaurant_id uuid NOT NULL REFERENCES restaurantpos.restaurants(id),
 category_id uuid,
 name text NOT NULL CHECK (length(name) BETWEEN 1 AND 140),
 description text NOT NULL DEFAULT '',
 price_minor integer NOT NULL CHECK (price_minor >= 0),
 active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE (restaurant_id, id),
 FOREIGN KEY (restaurant_id, category_id) REFERENCES restaurantpos.categories (restaurant_id, id)
);
CREATE INDEX IF NOT EXISTS rp_menu_restaurant_idx ON restaurantpos.menu_items(restaurant_id, active);
CREATE TABLE IF NOT EXISTS restaurantpos.dining_tables (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 restaurant_id uuid NOT NULL REFERENCES restaurantpos.restaurants(id),
 label text NOT NULL CHECK (length(label) BETWEEN 1 AND 40),
 seats integer NOT NULL DEFAULT 2 CHECK (seats BETWEEN 1 AND 40),
 active boolean NOT NULL DEFAULT true,
 UNIQUE (restaurant_id, label),
 UNIQUE (restaurant_id, id)
);
CREATE TABLE IF NOT EXISTS restaurantpos.orders (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 restaurant_id uuid NOT NULL REFERENCES restaurantpos.restaurants(id),
 order_number bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
 client_request_id uuid NOT NULL,
 table_id uuid,
 service_type text NOT NULL CHECK (service_type IN ('dine_in','takeout')),
 status text NOT NULL DEFAULT 'open' CHECK (status IN ('open','paid','cancelled')),
 kitchen_status text NOT NULL DEFAULT 'queued' CHECK (kitchen_status IN ('queued','preparing','ready','served')),
 notes text NOT NULL DEFAULT '',
 subtotal_minor integer NOT NULL CHECK (subtotal_minor >= 0),
 discount_minor integer NOT NULL DEFAULT 0 CHECK (discount_minor >= 0),
 service_charge_minor integer NOT NULL DEFAULT 0 CHECK (service_charge_minor >= 0),
 total_minor integer NOT NULL CHECK (total_minor >= 0),
 created_by uuid NOT NULL REFERENCES restaurantpos.staff(id),
 created_at timestamptz NOT NULL DEFAULT now(),
 paid_at timestamptz,
 UNIQUE (restaurant_id, client_request_id),
 UNIQUE (restaurant_id, id),
 FOREIGN KEY (restaurant_id, table_id) REFERENCES restaurantpos.dining_tables(restaurant_id, id),
 CHECK ((service_type = 'dine_in') OR table_id IS NULL),
 CHECK (total_minor = subtotal_minor - discount_minor + service_charge_minor)
);
CREATE UNIQUE INDEX IF NOT EXISTS rp_one_open_order_per_table ON restaurantpos.orders(table_id) WHERE status='open' AND table_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS rp_orders_restaurant_created ON restaurantpos.orders(restaurant_id, created_at DESC);
CREATE TABLE IF NOT EXISTS restaurantpos.order_items (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 restaurant_id uuid NOT NULL REFERENCES restaurantpos.restaurants(id),
 order_id uuid NOT NULL,
 menu_item_id uuid NOT NULL,
 item_name text NOT NULL,
 unit_price_minor integer NOT NULL CHECK (unit_price_minor >= 0),
 quantity integer NOT NULL CHECK (quantity BETWEEN 1 AND 99),
 notes text NOT NULL DEFAULT '',
 line_total_minor integer NOT NULL CHECK (line_total_minor = unit_price_minor * quantity),
 FOREIGN KEY (restaurant_id, order_id) REFERENCES restaurantpos.orders(restaurant_id, id),
 FOREIGN KEY (restaurant_id, menu_item_id) REFERENCES restaurantpos.menu_items(restaurant_id, id)
);
CREATE INDEX IF NOT EXISTS rp_order_items_order_idx ON restaurantpos.order_items(restaurant_id, order_id);
CREATE TABLE IF NOT EXISTS restaurantpos.payments (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 restaurant_id uuid NOT NULL REFERENCES restaurantpos.restaurants(id),
 order_id uuid NOT NULL UNIQUE,
 method text NOT NULL CHECK (method IN ('cash','gcash','maya','card')),
 amount_minor integer NOT NULL CHECK (amount_minor >= 0),
 tendered_minor integer NOT NULL CHECK (tendered_minor >= 0),
 change_minor integer NOT NULL CHECK (change_minor >= 0),
 reference text NOT NULL DEFAULT '',
 recorded_by uuid NOT NULL REFERENCES restaurantpos.staff(id),
 paid_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY (restaurant_id, order_id) REFERENCES restaurantpos.orders(restaurant_id, id)
);
CREATE INDEX IF NOT EXISTS rp_payments_restaurant_idx ON restaurantpos.payments(restaurant_id, paid_at DESC);
COMMIT;
