CREATE TABLE checkout_quote (
 id uuid PRIMARY KEY, tenant_id uuid NOT NULL, owner_id uuid NOT NULL,
 idempotency_key varchar(200) NOT NULL, request_hash text NOT NULL,
 importe numeric(10,2) NOT NULL CHECK (importe > 0), items text NOT NULL,
 expires_at timestamptz NOT NULL, pedido_id bigint, pago_id uuid UNIQUE,
 confirm_key varchar(200), UNIQUE(tenant_id,owner_id,idempotency_key)
);
CREATE TABLE checkout_outbox (
 event_id uuid PRIMARY KEY REFERENCES checkout_quote(id), payload text NOT NULL,
 published boolean NOT NULL DEFAULT false
);
