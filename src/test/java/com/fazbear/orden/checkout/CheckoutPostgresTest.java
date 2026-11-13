package com.fazbear.orden.checkout;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated real PostgreSQL only. Never connects to a legacy H2/R2 database. */
@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL",matches="jdbc:postgresql:.*")
class CheckoutPostgresTest {
    final String tenant="ee0d2c5b-e60b-4c57-9b30-8a38a520c430";
    final UUID owner=UUID.randomUUID(),paymentId=UUID.randomUUID();
    JdbcTemplate db; TransactionTemplate tx; CheckoutService service; HttpServer server;
    UUID operation; String paymentOwner; String paymentAmount; String paymentState;
    final AtomicInteger orders=new AtomicInteger();
    final Map<String,Object> createdOrders=new HashMap<>();
    String schema;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),System.getenv("TEST_DATABASE_USER"),System.getenv("TEST_DATABASE_PASSWORD"));
        schema="orden_test_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(ds).execute("create schema "+schema);
        ds.setUrl(System.getenv("TEST_DATABASE_URL")+(System.getenv("TEST_DATABASE_URL").contains("?")?"&":"?")+"currentSchema="+schema);
        db=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Flyway.configure().dataSource(ds).schemas(schema).load().migrate();
        paymentOwner=owner.toString(); paymentAmount="2500.00"; paymentState="APPROVED";
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            String path=exchange.getRequestURI().getPath(); String body;
            if (path.startsWith("/api/productos/")) {
                assertEquals("Bearer delegated",exchange.getRequestHeaders().getFirst("Authorization"));
                body="{\"id\":1,\"nombre\":\"Pizza\",\"precio\":1250,\"stock\":10}";
            } else if (path.startsWith("/internal/pagos/")) {
                assertEquals("test-service-key",exchange.getRequestHeaders().getFirst("X-Service-Key"));
                body="{\"pagoId\":\""+paymentId+"\",\"operacionId\":\""+operation+"\",\"tenantId\":\""+tenant
                    +"\",\"ownerId\":\""+paymentOwner+"\",\"importe\":"+paymentAmount+",\"moneda\":\"CLP\",\"estado\":\""+paymentState+"\",\"simulacion\":true,\"totalReembolsado\":0}";
            } else {
                assertEquals("test-service-key",exchange.getRequestHeaders().getFirst("X-Service-Key"));
                var payload=new com.fasterxml.jackson.databind.ObjectMapper().readValue(exchange.getRequestBody(),Map.class);
                assertEquals("2500.00",new BigDecimal(payload.get("importe").toString()).setScale(2).toString());
                createdOrders.computeIfAbsent(payload.get("operacionId").toString(),k -> orders.incrementAndGet());
                body="{\"pedidoId\":42}";
            }
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        service=new CheckoutService(db,base,base,base,"test-service-key",tenant,900);
    }
    @AfterEach void cleanup() {
        if(server!=null) server.stop(0);
        if(db!=null) db.execute("drop schema "+schema+" cascade");
    }
    Jwt jwt(UUID id) { return Jwt.withTokenValue("local").header("alg","RS256").claim("tid",tenant)
        .claim("oid",id.toString()).claim("scp","access_as_user").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build(); }
    Map<String,Object> quote(String key,int quantity) { return tx.execute(s -> service.quote(new CheckoutController.QuoteRequest(
        List.of(new CheckoutController.Item(1L,quantity))),key,"Bearer delegated",jwt(owner))); }
    Map<String,Object> confirm(String key) { return tx.execute(s -> service.confirm(new CheckoutController.Confirmation(operation,paymentId),key,jwt(owner))); }
    @Test void canonicalPriceImmutableAndDuplicateQuote() {
        var q=quote("quote-key",2); operation=(UUID)q.get("operacionId");
        assertEquals(new BigDecimal("2500.00"),q.get("importe"));
        assertEquals(q,quote("quote-key",2));
        assertThrows(ResponseStatusException.class,() -> quote("quote-key",3));
        assertThrows(ResponseStatusException.class,() -> service.get(operation,jwt(UUID.randomUUID())));
        assertEquals(1,db.queryForObject("select count(*) from checkout_quote",Integer.class));
    }
    @Test void rejectTamperedAmountOwnerAndUnapprovedPaymentThenRetryOnce() {
        operation=(UUID)quote("quote",2).get("operacionId");
        paymentAmount="1"; assertThrows(ResponseStatusException.class,() -> confirm("confirm"));
        paymentAmount="2500"; paymentOwner=UUID.randomUUID().toString(); assertThrows(ResponseStatusException.class,() -> confirm("confirm"));
        paymentOwner=owner.toString(); paymentState="REFUNDED"; assertThrows(ResponseStatusException.class,() -> confirm("confirm"));
        assertEquals(0,orders.get()); paymentState="APPROVED";
        var result=confirm("confirm"); assertEquals(result,confirm("confirm")); assertEquals(result,confirm("different-key"));
        assertEquals(1,orders.get());
        assertEquals(1,db.queryForObject("select count(*) from checkout_outbox",Integer.class));
        assertEquals(operation,db.queryForObject("select event_id from checkout_outbox",UUID.class));
    }
    @Test void expiredQuoteCannotCreateOrder() {
        operation=(UUID)quote("quote",2).get("operacionId");
        db.update("update checkout_quote set expires_at=now()-interval '1 second'");
        assertThrows(ResponseStatusException.class,() -> confirm("confirm")); assertEquals(0,orders.get());
    }
    @Test void lostLocalCommitRetriesSameExternalOperation() {
        operation=(UUID)quote("quote",2).get("operacionId");
        tx.execute(s -> { service.confirm(new CheckoutController.Confirmation(operation,paymentId),"confirm",jwt(owner)); s.setRollbackOnly(); return null; });
        assertEquals(0,db.queryForObject("select count(*) from checkout_outbox",Integer.class));
        confirm("confirm"); assertEquals(1,orders.get());
        assertEquals(1,db.queryForObject("select count(*) from checkout_outbox",Integer.class));
    }
}
