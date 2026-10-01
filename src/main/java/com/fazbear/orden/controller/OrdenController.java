package com.fazbear.orden.controller;

import com.fazbear.orden.model.ConfirmarOrdenRequest;
import com.fazbear.orden.service.OrdenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OrdenController — expone el endpoint de confirmación de compra.
 *
 * POST /api/orden/confirmar
 *   Body: { usuarioId, emailUsuario, items: [{productoId, nombreProducto, cantidad, precioUnitario}] }
 *   Auth: Bearer Token (Azure Entra ID)
 */
@RestController
@RequestMapping("/api/orden")
@CrossOrigin(origins = "https://w8xu8o4pd7.execute-api.us-east-1.amazonaws.com")
public class OrdenController {

    private final OrdenService ordenService;

    public OrdenController(OrdenService ordenService) {
        this.ordenService = ordenService;
    }

    /**
     * Confirma una orden de compra:
     *  1. Crea el pedido en ms-pedidos
     *  2. Publica evento en RabbitMQ (para ms-notificaciones y ms-reportes)
     *  3. Vacía el carrito del usuario en ms-carrito
     */
    @PostMapping("/confirmar")
    public ResponseEntity<Map<String, Object>> confirmarOrden(
            @RequestBody ConfirmarOrdenRequest request,
            @RequestHeader("Authorization") String bearerToken) {
        Map<String, Object> resultado = ordenService.confirmarOrden(request, bearerToken);
        return ResponseEntity.ok(resultado);
    }

    /** Health check — no requiere auth en este endpoint. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ms-orden"));
    }
}
