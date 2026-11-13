# Trusted checkout — isolated EV3 local only

Run `Dockerfile.local` / `SPRING_PROFILES_ACTIVE=ev3-local`. Port **8080**.
Required: dedicated PostgreSQL (`ORDEN_DB_URL`, `ORDEN_DB_USER`, `ORDEN_DB_PASSWORD`),
`PRODUCTOS_URL`, `PAGO_URL`, `PEDIDOS_URL`, `INTERNAL_SERVICE_KEY` (same secret in Orden/Pago/Pedidos).
Rabbit: `RABBITMQ_HOST/PORT/USER/PASS/VHOST`; publisher confirmations are enabled.
Optional `QUOTE_TTL_SECONDS` (default 900), `LOCAL_JWT_JWK_SET_URI` (only mapped in this profile).
Azure issuer/audience/tenant remain unchanged; `tid`, UUID `oid`, delegated `access_as_user` required.
Never point local settings at historical databases or AWS. Flyway manages only the dedicated local database.

## Owner endpoints (Bearer JWT)

* `POST /api/orden/cotizaciones`, `Idempotency-Key`: `{"items":[{"productoId":1,"cantidad":2}]}`.
  No caller prices/names/owner are authoritative. Products fetched individually with forwarded bearer.
  Reject duplicates, invalid quantity, missing product/price/stock, insufficient stock and explicit `activo:false`.
  Current catalog has no active flag: existence is its availability contract. If supplied later, false is rejected.
  Snapshot prices must be positive with <=2 decimal places; maximum total 99999999.99 CLP.
  Response: `{operacionId,tenantId,ownerId,importe,moneda:"CLP",expiraEn,items}`; item snapshot includes
  `productoId,nombreProducto,cantidad,precioUnitario`. Same key+canonical items returns same immutable quote;
  changed items returns 409. A reused expired quote stays expired; create a new key to refresh.
* `GET /api/orden/cotizaciones/{uuid}` returns same snapshot, owner only (404 otherwise).
  Expiry remains visible so Pago can reject expired snapshots; GET itself does not consume quote.
* `POST /api/orden/confirmar-pago`, `Idempotency-Key`: `{"operacionId":"uuid","pagoId":"uuid"}`.
  Before first creation requires unexpired quote and authoritative Pago `GET /internal/pagos/{id}` with
  `X-Service-Key`; matches all IDs, tenant, owner, amount, CLP, APPROVED, `simulacion:true`, zero refunded total.
  Calls Pedidos `POST /internal/pedidos/pagados` with snapshot and same operation/payment references.
  Returns `{pedidoId,mensaje,estado:"PENDIENTE",total,pagoId,simulacion:true}`.
  Replay of confirmed same operation/payment returns original order; a different payment is 409.
* Legacy `POST /api/orden/confirmar` returns **409 USE_PAYMENT_FLOW** only in this profile.

## Delivery and limits

Order creation is idempotent in Pedidos (unique operation AND payment). Row/advisory PostgreSQL locks serialize
concurrent requests. Confirmation and durable outbox are committed together locally. HTTP calls have bounded timeouts.
Rabbit retries an unsent outbox every 3s, with broker confirms and stable `eventoId=operacionId`; at-least-once,
not exactly-once. Existing operational exchange/routing/pedido items remain unchanged; stock consumers must deduplicate.
If Pedidos succeeds but local commit is lost, retry same operation/payment before quote expiry: Pedidos returns same order.
An expired ambiguous attempt requires operator reconciliation, never a second charge/order with invented references.
No distributed transaction with refund/stock; catalog stock is checked, not reserved. Concurrent refunds can race a
confirmation; this is an explicit simulation limit. Outbox may still be pending when HTTP confirmation succeeds.
No automatic cart deletion (avoid losing concurrently added items). No bank claim; simulated payments only.
Pedidos cancellation is not a refund; call Pago owner `POST /api/pagos/{id}/anulacion` separately.

## Validation

`mvn verify` runs legacy tests on disposable in-memory databases; no historical H2/R2 files.
Set `TEST_DATABASE_URL=jdbc:postgresql://localhost:55439/ev3test`, `TEST_DATABASE_USER/PASSWORD` to enable
real PostgreSQL tests. Tests create and drop only random `orden_test_*` schemas; local HTTP adapters mock
catalog/payment/order authority. CI supplies isolated PostgreSQL and builds Docker locally without registry login/push.
