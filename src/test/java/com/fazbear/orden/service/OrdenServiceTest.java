package com.fazbear.orden.service;

import com.fazbear.orden.config.RabbitMQConfig;
import com.fazbear.orden.model.ConfirmarOrdenRequest;
import com.fazbear.orden.model.ItemOrdenRequest;
import com.fazbear.orden.model.PedidoEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrdenServiceTest {
    private HttpServer server;
    private OrdenService service;
    private RabbitTemplate rabbit;
    private final AtomicInteger postCount = new AtomicInteger();
    private final AtomicInteger deleteCount = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(201);
    private final AtomicReference<String> body = new AtomicReference<>("{\"id\":42}");
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/pedidos", exchange -> {
            postCount.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            byte[] payload = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.createContext("/carrito", exchange -> {
            deleteCount.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        rabbit = mock(RabbitTemplate.class);
        service = new OrdenService(rabbit, WebClient.builder());
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ReflectionTestUtils.setField(service, "msPedidosUrl", base);
        ReflectionTestUtils.setField(service, "msCarritoUrl", base);
    }

    @AfterEach
    void cleanup() {
        server.stop(0);
    }

    static ItemOrdenRequest item(Long id, int cantidad, BigDecimal precio) {
        ItemOrdenRequest item = new ItemOrdenRequest();
        item.setProductoId(id);
        item.setNombreProducto("Pizza");
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precio);
        return item;
    }

    private ConfirmarOrdenRequest request(List<ItemOrdenRequest> items) {
        ConfirmarOrdenRequest request = new ConfirmarOrdenRequest();
        request.setUsuarioId("test-user");
        request.setEmailUsuario("test@example.com");
        request.setItems(items);
        return request;
    }

    @Test
    void publishesV2EventWithStockItemsAndUniqueEventIdAfterCreatingOrder() {
        var request = request(List.of(item(3L, 2, new BigDecimal("12.99"))));
        var result = service.confirmarOrden(request, "Bearer test-token");
        ArgumentCaptor<PedidoEvent> captor = ArgumentCaptor.forClass(PedidoEvent.class);
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(RabbitMQConfig.ROUTING_KEY), captor.capture());
        PedidoEvent event = captor.getValue();
        assertEquals(42L, event.getPedidoId());
        assertNotNull(UUID.fromString(event.getEventoId()));
        assertEquals(2, event.getSchemaVersion());
        assertEquals(List.of(new PedidoEvent.ItemStock(3L, 2)), event.getItems());
        assertEquals(0, new BigDecimal("25.98").compareTo(event.getTotal()));
        assertEquals(42L, result.get("pedidoId"));
        assertEquals("Bearer test-token", authorization.get());
        assertEquals(1, postCount.get());
        assertEquals(1, deleteCount.get());
    }

    static Stream<List<ItemOrdenRequest>> invalidItems() {
        return Stream.of(null, List.of(), Arrays.asList((ItemOrdenRequest) null),
            List.of(item(null, 1, BigDecimal.ONE)), List.of(item(1L, 0, BigDecimal.ONE)),
            List.of(item(1L, -1, BigDecimal.ONE)), List.of(item(1L, 1, null)),
            List.of(item(1L, 1, new BigDecimal("-1"))));
    }

    @ParameterizedTest
    @MethodSource("invalidItems")
    void invalidItemsAreRejectedBeforeCreatingAnOrder(List<ItemOrdenRequest> items) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.confirmarOrden(request(items), "Bearer test-token"));
        assertEquals(400, error.getStatusCode().value());
        assertEquals(0, postCount.get());
        verifyNoInteractions(rabbit);
    }

    @Test
    void orderServiceFailureDoesNotPublishOrClearTheCart() {
        status.set(500);
        assertThrows(RuntimeException.class, () -> service.confirmarOrden(
            request(List.of(item(1L, 1, BigDecimal.ONE))), "Bearer test-token"));
        verifyNoInteractions(rabbit);
        assertEquals(0, deleteCount.get());
    }

    @Test
    void missingOrderIdDoesNotCreateAGhostStockEvent() {
        body.set("{}");
        assertThrows(IllegalStateException.class, () -> service.confirmarOrden(
            request(List.of(item(1L, 1, BigDecimal.ONE))), "Bearer test-token"));
        verifyNoInteractions(rabbit);
        assertEquals(0, deleteCount.get());
    }

    @Test
    void brokerFailureDoesNotPretendToConfirmOrClearTheCart() {
        doThrow(new AmqpException("Broker unavailable")).when(rabbit)
            .convertAndSend(anyString(), anyString(), any(PedidoEvent.class));
        assertThrows(AmqpException.class, () -> service.confirmarOrden(
            request(List.of(item(1L, 1, BigDecimal.ONE))), "Bearer test-token"));
        assertEquals(1, postCount.get()); // Limitación actual: el pedido ya se creó antes del fallo AMQP.
        assertEquals(0, deleteCount.get());
    }
}
