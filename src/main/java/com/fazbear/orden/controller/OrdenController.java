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
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

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
            @RequestHeader("Authorization") String bearerToken,
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        if (environment.matchesProfiles("ev3-local")) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "USE_PAYMENT_FLOW");
        }
        String tenant = "ee0d2c5b-e60b-4c57-9b30-8a38a520c430";
        String oid = jwt == null ? null : jwt.getClaimAsString("oid");
        String scope = jwt == null ? null : jwt.getClaimAsString("scp");
        if (oid == null || !oid.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                || !tenant.equals(jwt.getClaimAsString("tid")) || scope == null
                || !java.util.Arrays.asList(scope.split("\\s+")).contains("access_as_user")
                || request == null || !oid.equals(request.getUsuarioId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,"OWNER_REQUIRED");
        }
        Map<String, Object> resultado = ordenService.confirmarOrden(request, bearerToken);
        return ResponseEntity.ok(resultado);
    }

    /** Health check — no requiere auth en este endpoint. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "ms-orden"));
    }
}
