package com.fazbear.orden.model;

import java.math.BigDecimal;

/** Un item dentro de la solicitud de confirmación de orden. */
public class ItemOrdenRequest {

    private Long productoId;
    private String nombreProducto;
    private int cantidad;
    private BigDecimal precioUnitario;

    public ItemOrdenRequest() {}

    public Long getProductoId()              { return productoId; }
    public void setProductoId(Long v)        { this.productoId = v; }

    public String getNombreProducto()        { return nombreProducto; }
    public void setNombreProducto(String v)  { this.nombreProducto = v; }

    public int getCantidad()                 { return cantidad; }
    public void setCantidad(int v)           { this.cantidad = v; }

    public BigDecimal getPrecioUnitario()         { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal v)   { this.precioUnitario = v; }
}
