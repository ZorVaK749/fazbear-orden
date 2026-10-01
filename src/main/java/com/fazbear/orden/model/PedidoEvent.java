package com.fazbear.orden.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private LocalDateTime fechaCreacion;

    public PedidoEvent() {}

    public PedidoEvent(Long pedidoId, String usuarioId, String emailUsuario,
                       BigDecimal total, String estado, LocalDateTime fechaCreacion) {
        this.pedidoId      = pedidoId;
        this.usuarioId     = usuarioId;
        this.emailUsuario  = emailUsuario;
        this.total         = total;
        this.estado        = estado;
        this.fechaCreacion = fechaCreacion;
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

    public LocalDateTime getFechaCreacion()        { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime v)  { this.fechaCreacion = v; }
}
