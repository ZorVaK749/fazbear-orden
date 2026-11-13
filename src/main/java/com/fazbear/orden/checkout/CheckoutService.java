package com.fazbear.orden.checkout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazbear.orden.model.PedidoEvent;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("ev3-local")
public class CheckoutService {
    private final JdbcTemplate db;
    private final RestClient http = RestClient.builder()
        .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {{
                setReadTimeout(Duration.ofSeconds(15));
            }}).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String productos, pago, pedidos, serviceKey, tenant;
    private final long ttl;
    public CheckoutService(JdbcTemplate db,
            @Value("${checkout.productos-url}") String productos,
            @Value("${checkout.pago-url}") String pago,
            @Value("${checkout.pedidos-url}") String pedidos,
            @Value("${checkout.service-key}") String serviceKey,
            @Value("${spring.security.oauth2.resourceserver.jwt.tenant-id}") String tenant,
            @Value("${checkout.ttl-seconds:900}") long ttl) {
        if (serviceKey.isBlank() || ttl < 1) throw new IllegalArgumentException("Checkout configuration missing");
        this.db=db; this.productos=productos; this.pago=pago; this.pedidos=pedidos;
        this.serviceKey=serviceKey; this.tenant=tenant; this.ttl=ttl;
    }
    private UUID owner(Jwt jwt) {
        try {
            if (jwt == null || !tenant.equals(jwt.getClaimAsString("tid"))
                    || !Arrays.asList(jwt.getClaimAsString("scp").split("\\s+")).contains("access_as_user"))
                throw new IllegalArgumentException();
            String oid = jwt.getClaimAsString("oid");
            UUID id = UUID.fromString(oid);
            if (!id.toString().equalsIgnoreCase(oid)) throw new IllegalArgumentException();
            return id;
        } catch (RuntimeException e) { throw error(HttpStatus.FORBIDDEN,"OWNER_REQUIRED"); }
    }
    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status,message);
    }
    private void key(String key) {
        if (key == null || key.isBlank() || key.length()>200) throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED");
    }
    private void lock(String value) {
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))",value);
    }
    @Transactional
    public Map<String,Object> quote(CheckoutController.QuoteRequest request, String key, String bearer, Jwt jwt) {
        UUID owner=owner(jwt); key(key);
        if (request == null || request.items()==null || request.items().isEmpty() || request.items().size()>100)
            throw error(HttpStatus.BAD_REQUEST,"ITEMS_REQUIRED");
        TreeMap<Long,Integer> canonical=new TreeMap<>();
        for (var i:request.items()) {
            if (i==null || i.productoId()==null || i.productoId()<1 || i.cantidad()==null
                    || i.cantidad()<1 || i.cantidad()>10000 || canonical.putIfAbsent(i.productoId(),i.cantidad())!=null)
                throw error(HttpStatus.BAD_REQUEST,"INVALID_OR_DUPLICATE_ITEM");
        }
        String fingerprint=encode(canonical);
        lock(tenant+owner+key);
        var existing=db.queryForList("select * from checkout_quote where tenant_id=? and owner_id=? and idempotency_key=?",
            UUID.fromString(tenant),owner,key);
        if (!existing.isEmpty()) {
            if (!fingerprint.equals(existing.getFirst().get("request_hash"))) throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");
            return view(existing.getFirst());
        }
        List<Map<String,Object>> items=new ArrayList<>(); BigDecimal total=BigDecimal.ZERO;
        for (var i:canonical.entrySet()) {
            Map<?,?> product;
            try { product=http.get().uri(productos+"/api/productos/"+i.getKey())
                .header("Authorization",bearer).retrieve().body(Map.class); }
            catch (RuntimeException e) { throw error(HttpStatus.BAD_GATEWAY,"CATALOG_UNAVAILABLE"); }
            if (product==null || !(product.get("stock") instanceof Number stock) || stock.intValue()<i.getValue()
                    || Boolean.FALSE.equals(product.get("activo")) || product.get("precio")==null
                    || !(product.get("nombre") instanceof String name) || name.isBlank() || name.length()>120)
                throw error(HttpStatus.CONFLICT,"PRODUCT_UNAVAILABLE");
            BigDecimal price;
            try { price=new BigDecimal(product.get("precio").toString()).setScale(2,java.math.RoundingMode.UNNECESSARY); }
            catch (RuntimeException e) { throw error(HttpStatus.CONFLICT,"INVALID_CATALOG_PRICE"); }
            if (price.signum()<=0) throw error(HttpStatus.CONFLICT,"INVALID_CATALOG_PRICE");
            items.add(Map.of("productoId",i.getKey(),"cantidad",i.getValue(),"nombreProducto",product.get("nombre"),"precioUnitario",price));
            total=total.add(price.multiply(BigDecimal.valueOf(i.getValue())));
        }
        if (total.compareTo(new BigDecimal("99999999.99"))>0) throw error(HttpStatus.BAD_REQUEST,"TOTAL_LIMIT");
        UUID id=UUID.randomUUID();
        db.update("insert into checkout_quote(id,tenant_id,owner_id,idempotency_key,request_hash,importe,items,expires_at) values(?,?,?,?,?,?,?,?)",
            id,UUID.fromString(tenant),owner,key,fingerprint,total,encode(items),OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(ttl));
        return view(row(id,owner,false));
    }
    @Transactional(readOnly=true)
    public Map<String,Object> get(UUID id, Jwt jwt) { return view(row(id,owner(jwt),false)); }
    private Map<String,Object> row(UUID id, UUID owner, boolean lock) {
        var rows=db.queryForList("select * from checkout_quote where id=? and tenant_id=? and owner_id=?"+(lock?" for update":""),
            id,UUID.fromString(tenant),owner);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"QUOTE_NOT_FOUND");
        return rows.getFirst();
    }
    private Map<String,Object> view(Map<String,Object> row) {
        return Map.of("operacionId",row.get("id"),"tenantId",row.get("tenant_id"),"ownerId",row.get("owner_id"),
            "importe",row.get("importe"),"moneda","CLP","expiraEn",expiry(row).toString(),"items",decode(row.get("items").toString()));
    }
    private Instant expiry(Map<String,Object> row) {
        Object value=row.get("expires_at");
        return value instanceof OffsetDateTime t ? t.toInstant() : ((java.sql.Timestamp)value).toInstant();
    }
    @Transactional
    public Map<String,Object> confirm(CheckoutController.Confirmation request, String key, Jwt jwt) {
        UUID owner=owner(jwt); key(key);
        if (request==null || request.operacionId()==null || request.pagoId()==null) throw error(HttpStatus.BAD_REQUEST,"PAYMENT_REQUIRED");
        lock(tenant+owner.toString()+"confirm:"+key);
        var used=db.queryForList("select id,pago_id from checkout_quote where tenant_id=? and owner_id=? and confirm_key=?",
            UUID.fromString(tenant),owner,key);
        if (!used.isEmpty() && (!request.operacionId().equals(used.getFirst().get("id"))
                || !request.pagoId().equals(used.getFirst().get("pago_id")))) throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");
        var q=row(request.operacionId(),owner,true);
        if (q.get("pedido_id")!=null) {
            if (!request.pagoId().equals(q.get("pago_id"))) throw error(HttpStatus.CONFLICT,"PAYMENT_CONFLICT");
            return result(q.get("pedido_id"),q.get("importe"),request.pagoId());
        }
        if (!expiry(q).isAfter(Instant.now())) throw error(HttpStatus.CONFLICT,"QUOTE_EXPIRED");
        Map<?,?> payment;
        try { payment=http.get().uri(pago+"/internal/pagos/"+request.pagoId()).header("X-Service-Key",serviceKey)
            .retrieve().body(Map.class); }
        catch (RuntimeException e) { throw error(HttpStatus.BAD_GATEWAY,"PAYMENT_UNAVAILABLE"); }
        if (!matches(payment,"pagoId",request.pagoId()) || !matches(payment,"operacionId",request.operacionId())
                || !matches(payment,"tenantId",tenant) || !matches(payment,"ownerId",owner)
                || !matches(payment,"moneda","CLP") || !matches(payment,"estado","APPROVED")
                || !Boolean.TRUE.equals(payment.get("simulacion")) || !amount(payment.get("importe"),(BigDecimal)q.get("importe"))
                || !amount(payment.get("totalReembolsado"),BigDecimal.ZERO)) throw error(HttpStatus.CONFLICT,"PAYMENT_MISMATCH");
        var payload=new LinkedHashMap<>(view(q)); payload.remove("expiraEn");
        payload.put("pagoId",request.pagoId()); payload.put("simulacion",true);
        Map<?,?> order;
        try { order=http.post().uri(pedidos+"/internal/pedidos/pagados").header("X-Service-Key",serviceKey)
            .body(payload).retrieve().body(Map.class); }
        catch (RuntimeException e) { throw error(HttpStatus.BAD_GATEWAY,"PEDIDOS_UNAVAILABLE_RETRY_SAME_OPERATION"); }
        if (order==null || !(order.get("pedidoId") instanceof Number id) || id.longValue()<1)
            throw error(HttpStatus.BAD_GATEWAY,"INVALID_PEDIDOS_RESPONSE");
        db.update("update checkout_quote set pedido_id=?,pago_id=?,confirm_key=? where id=?",id.longValue(),request.pagoId(),key,request.operacionId());
        PedidoEvent event=new PedidoEvent(id.longValue(),owner.toString(),"",(BigDecimal)q.get("importe"),"PENDIENTE",LocalDateTime.now().toString());
        event.setEventoId(request.operacionId().toString());
        event.setItems(decode(q.get("items").toString()).stream().map(i -> new PedidoEvent.ItemStock(
            ((Number)i.get("productoId")).longValue(),((Number)i.get("cantidad")).intValue())).toList());
        db.update("insert into checkout_outbox(event_id,payload) values(?,?)",request.operacionId(),encode(event));
        return result(id.longValue(),q.get("importe"),request.pagoId());
    }
    private boolean matches(Map<?,?> m,String k,Object value) { return m!=null && value.toString().equals(String.valueOf(m.get(k))); }
    private boolean amount(Object value,BigDecimal expected) {
        try { return value!=null && new BigDecimal(value.toString()).compareTo(expected)==0; }
        catch (RuntimeException e) { return false; }
    }
    private Map<String,Object> result(Object id,Object total,UUID pagoId) {
        return Map.of("pedidoId",id,"mensaje","Pedido confirmado con pago SIMULADO; carrito conservado",
            "estado","PENDIENTE","total",total,"pagoId",pagoId,"simulacion",true);
    }
    String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private List<Map<String,Object>> decode(String value) {
        try { return json.readValue(value,new TypeReference<>(){}); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
