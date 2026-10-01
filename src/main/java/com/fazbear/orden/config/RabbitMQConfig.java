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
 *   Queue:       pedido.creado.queue
 *   Routing key: pedido.creado
 *
 * ms-orden PUBLICA en esta cola cuando se confirma una compra.
 * ms-notificaciones y ms-reportes ESCUCHAN esta cola.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE    = "pedidos.exchange";
    public static final String QUEUE       = "pedido.creado.queue";
    public static final String ROUTING_KEY = "pedido.creado";

    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue pedidoCreadoQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding binding(Queue pedidoCreadoQueue, DirectExchange pedidosExchange) {
        return BindingBuilder.bind(pedidoCreadoQueue).to(pedidosExchange).with(ROUTING_KEY);
    }

    /** Serializa/deserializa mensajes como JSON automáticamente. */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
