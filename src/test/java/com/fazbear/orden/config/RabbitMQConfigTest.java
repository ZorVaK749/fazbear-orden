package com.fazbear.orden.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {
    @Test
    void eachConsumerHasItsOwnDurableQueueAndBinding() {
        var declarations = new RabbitMQConfig().pedidoCreadoQueues();
        var queues = declarations.getDeclarablesByType(Queue.class);
        var bindings = declarations.getDeclarablesByType(Binding.class);
        for (String service : java.util.List.of("notificaciones", "reportes", "productos")) {
            String name = "pedido.creado." + service + ".queue";
            assertTrue(queues.stream().anyMatch(q -> q.getName().equals(name) && q.isDurable()));
            assertTrue(bindings.stream().anyMatch(b -> b.getDestination().equals(name)
                && b.getExchange().equals(RabbitMQConfig.EXCHANGE)
                && b.getRoutingKey().equals(RabbitMQConfig.ROUTING_KEY)));
            assertTrue(queues.stream().anyMatch(q -> q.getName().equals(name + ".dlq")));
        }
        assertEquals(6, queues.size());
    }
}
