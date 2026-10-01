package com.fazbear.orden.model;

import java.util.List;

/**
 * Body de la petición POST /api/orden/confirmar
 * El frontend envía el usuarioId y la lista de items del carrito.
 */
public class ConfirmarOrdenRequest {

    private String usuarioId;
    private String emailUsuario;
    private List<ItemOrdenRequest> items;

    public ConfirmarOrdenRequest() {}

    public String getUsuarioId()                   { return usuarioId; }
    public void setUsuarioId(String v)             { this.usuarioId = v; }

    public String getEmailUsuario()                { return emailUsuario; }
    public void setEmailUsuario(String v)          { this.emailUsuario = v; }

    public List<ItemOrdenRequest> getItems()       { return items; }
    public void setItems(List<ItemOrdenRequest> v) { this.items = v; }
}
