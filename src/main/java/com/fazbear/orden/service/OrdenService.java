package com.fazbear.orden.service;

import com.fazbear.orden.config.RabbitMQConfig;
import com.fazbear.orden.model.ConfirmarOrdenRequest;
import com.fazbear.orden.model.ItemOrdenRequest;
import com.fazbear.orden.model.PedidoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OrdenService — orquesta el flujo de confirmación de compra:
 *  1. Calcula el total de los items.
 *  2. Crea el pedido en ms-pedidos via HTTP.
 *  3. Publica el evento pedido.creado en RabbitMQ.
 *  4. Vacía el carrito del usuario en ms-carrito via HTTP.
 */
@Service
public class OrdenService {

    private static final Logger log = LoggerFactory.getLogger(OrdenService.class);

    private final RabbitTemplate rabbitTemplate;
    private final WebClient webClient;

    @Value("${ms.pedidos.url}")
    private String msPedidosUrl;

    @Value("${ms.carrito.url}")
    private String msCarritoUrl;

    public OrdenService(RabbitTemplate rabbitTemplate, WebClient.Builder webClientBuilder) {
        this.rabbitTemplate = rabbitTemplate;
        this.webClient      = webClientBuilder.build();
    }

    /**
     * Procesa la confirmación de una orden:
     *  - Se comunica con ms-pedidos para crear el pedido
     *  - Publica en RabbitMQ el evento de pedido creado
     *  - Vacía el carrito del usuario
     *
     * @param request  datos de la orden enviados por el frontend
     * @param bearerToken token JWT del usuario (para autenticar en otros MS)
     * @return mapa con el resultado de la operación
     */
    public Map<String, Object> confirmarOrden(ConfirmarOrdenRequest request, String bearerToken) {

        // 1. Calcular total
        BigDecimal total = request.getItems().stream()
            .map(i -> i.getPrecioUnitario().multiply(BigDecimal.valueOf(i.getCantidad())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Construir payload para ms-pedidos
        Map<String, Object> pedidoPayload = new HashMap<>();
        pedidoPayload.put("usuarioId",    request.getUsuarioId());
        pedidoPayload.put("emailUsuario", request.getEmailUsuario());
        pedidoPayload.put("total",        total);
        pedidoPayload.put("estado",       "PENDIENTE");
        pedidoPayload.put("items",        buildItemsPayload(request.getItems()));

        // 3. Crear pedido en ms-pedidos
        Map<?, ?> pedidoCreado = webClient.post()
            .uri(msPedidosUrl + "/pedidos")
            .header("Authorization", bearerToken)
            .header("Content-Type", "application/json")
            .bodyValue(pedidoPayload)
            .retrieve()
            .bodyToMono(Map.class)
            .doOnError(e -> log.error("Error al crear pedido en ms-pedidos: {}", e.getMessage()))
            .block();

        Long pedidoId = null;
        if (pedidoCreado != null && pedidoCreado.get("id") instanceof Number) {
            pedidoId = ((Number) pedidoCreado.get("id")).longValue();
        }

        // 4. Publicar evento en RabbitMQ
        PedidoEvent evento = new PedidoEvent(
            pedidoId,
            request.getUsuarioId(),
            request.getEmailUsuario(),
            total,
            "PENDIENTE",
            LocalDateTime.now()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, evento);
        log.info("Evento pedido.creado publicado en RabbitMQ para pedidoId={}", pedidoId);

        // 5. Vaciar carrito del usuario
        try {
            webClient.delete()
                .uri(msCarritoUrl + "/carrito/" + request.getUsuarioId())
                .header("Authorization", bearerToken)
                .retrieve()
                .bodyToMono(Void.class)
                .block();
            log.info("Carrito vaciado para usuarioId={}", request.getUsuarioId());
        } catch (Exception e) {
            log.warn("No se pudo vaciar el carrito (no crítico): {}", e.getMessage());
        }

        // 6. Retornar respuesta
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("mensaje",  "Orden confirmada con éxito");
        respuesta.put("pedidoId", pedidoId);
        respuesta.put("total",    total);
        respuesta.put("estado",   "PENDIENTE");
        return respuesta;
    }

    private List<Map<String, Object>> buildItemsPayload(List<ItemOrdenRequest> items) {
        return items.stream().map(i -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productoId",      i.getProductoId());
            m.put("nombreProducto",  i.getNombreProducto());
            m.put("cantidad",        i.getCantidad());
            m.put("precioUnitario",  i.getPrecioUnitario());
            return m;
        }).toList();
    }
}
