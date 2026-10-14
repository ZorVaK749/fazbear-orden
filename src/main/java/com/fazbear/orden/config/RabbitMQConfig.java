package com.fazbear.orden.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ para ms-orden.
 *
 * Topología:
 *   Exchange:    pedidos.exchange  (DirectExchange)
 *   Queues:      pedido.creado.notificaciones.queue / pedido.creado.reportes.queue
 *   Routing key: pedido.creado
 *
 * ms-orden publica al exchange; cada servicio recibe su propia copia.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE    = "pedidos.exchange";
    public static final String ROUTING_KEY = "pedido.creado";

    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Declarables pedidoCreadoQueues() {
        DirectExchange exchange = pedidosExchange();
        DirectExchange deadLetterExchange = new DirectExchange("pedidos.dlx", true, false);
        java.util.List<Declarable> declarations = new java.util.ArrayList<>();
        declarations.add(deadLetterExchange);
        for (String service : java.util.List.of("notificaciones", "reportes")) {
            String name = "pedido.creado." + service + ".queue";
            Queue queue = QueueBuilder.durable(name)
                .deadLetterExchange("pedidos.dlx").deadLetterRoutingKey(name).build();
            Queue deadLetterQueue = QueueBuilder.durable(name + ".dlq").build();
            declarations.add(queue);
            declarations.add(deadLetterQueue);
            declarations.add(BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY));
            declarations.add(BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(name));
        }
        return new Declarables(declarations);
    }

    /** Serializa/deserializa mensajes como JSON automáticamente. */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
