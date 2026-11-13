package com.fazbear.orden.checkout;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("ev3-local")
@RequestMapping("/api/orden")
public class CheckoutController {
    private final CheckoutService service;
    public CheckoutController(CheckoutService service) { this.service = service; }
    public record Item(Long productoId, Integer cantidad) {}
    public record QuoteRequest(List<Item> items) {}
    public record Confirmation(UUID operacionId, UUID pagoId) {}

    @PostMapping("/cotizaciones")
    public Map<String,Object> quote(@RequestBody QuoteRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("Authorization") String bearer, @AuthenticationPrincipal Jwt jwt) {
        return service.quote(request, key, bearer, jwt);
    }
    @GetMapping("/cotizaciones/{id}")
    public Map<String,Object> get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(id, jwt);
    }
    @PostMapping("/confirmar-pago")
    public Map<String,Object> confirm(@RequestBody Confirmation request,
            @RequestHeader("Idempotency-Key") String key, @AuthenticationPrincipal Jwt jwt) {
        return service.confirm(request, key, jwt);
    }
}
