package com.fazbear.orden.model;

import com.fazbear.orden.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PedidoEventTest {
    @Test
    void serializesTheVersionedStockContractWithoutLosingExistingFields() throws Exception {
        PedidoEvent event = new PedidoEvent(1L, "user", "test@example.com", new BigDecimal("12.99"),
            "PENDIENTE", "2026-10-14T12:00:00");
        event.setItems(List.of(new PedidoEvent.ItemStock(3L, 2)));
        var message = new RabbitMQConfig().messageConverter().toMessage(event, new MessageProperties());
        var json = new ObjectMapper().readTree(message.getBody());
        assertEquals(2, json.get("schemaVersion").asInt());
        assertEquals(event.getEventoId(), json.get("eventoId").asText());
        assertEquals(1, json.get("pedidoId").asLong());
        assertEquals("test@example.com", json.get("emailUsuario").asText());
        assertEquals(3, json.get("items").get(0).get("productoId").asLong());
        assertEquals(2, json.get("items").get(0).get("cantidad").asInt());
        PedidoEvent another = new PedidoEvent(1L, "user", "test@example.com", BigDecimal.ONE,
            "PENDIENTE", "2026-10-14T12:00:00");
        assertNotEquals(event.getEventoId(), another.getEventoId());
    }
}
