package com.fazbear.orden.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Evento publicado en RabbitMQ cuando se confirma una orden.
 * Este mismo objeto es recibido por ms-notificaciones y ms-reportes.
 */
public class PedidoEvent {

    private Long pedidoId;
    private String usuarioId;
    private String emailUsuario;
    private BigDecimal total;
    private String estado;
    private String fechaCreacion;
    private String eventoId;
    private int schemaVersion = 2;
    private List<ItemStock> items = List.of();

    public record ItemStock(Long productoId, int cantidad) {}

    public PedidoEvent() {}

    public PedidoEvent(Long pedidoId, String usuarioId, String emailUsuario,
                       BigDecimal total, String estado, String fechaCreacion) {
        this.pedidoId      = pedidoId;
        this.usuarioId     = usuarioId;
        this.emailUsuario  = emailUsuario;
        this.total         = total;
        this.estado        = estado;
        this.fechaCreacion = fechaCreacion;
        this.eventoId      = UUID.randomUUID().toString();
    }

    public Long getPedidoId()              { return pedidoId; }
    public void setPedidoId(Long v)        { this.pedidoId = v; }

    public String getUsuarioId()           { return usuarioId; }
    public void setUsuarioId(String v)     { this.usuarioId = v; }

    public String getEmailUsuario()        { return emailUsuario; }
    public void setEmailUsuario(String v)  { this.emailUsuario = v; }

    public BigDecimal getTotal()           { return total; }
    public void setTotal(BigDecimal v)     { this.total = v; }

    public String getEstado()              { return estado; }
    public void setEstado(String v)        { this.estado = v; }

    public String getFechaCreacion()        { return fechaCreacion; }
    public void setFechaCreacion(String v)  { this.fechaCreacion = v; }

    public String getEventoId() { return eventoId; }
    public void setEventoId(String v) { this.eventoId = v; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int v) { this.schemaVersion = v; }
    public List<ItemStock> getItems() { return items; }
    public void setItems(List<ItemStock> v) { this.items = List.copyOf(v); }
}
