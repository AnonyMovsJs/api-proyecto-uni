package com.ronald.proyecto.proyecto_uni.dto;

import java.math.BigDecimal;
import java.util.List;

import com.ronald.proyecto.proyecto_uni.entity.Venta;

public class VentaDTO {
    private Long clienteId;
    private String descripcion;
    private BigDecimal montoTotal;
    private Venta.TipoVenta tipoVenta;
    private List<DetalleVentaDTO> detalles;
    private CreditoDTO creditoDTO; // Solo si es venta a crédito
    
    public Long getClienteId() {
        return clienteId;
    }
    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }
    public String getDescripcion() {
        return descripcion;
    }
    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
    public BigDecimal getMontoTotal() {
        return montoTotal;
    }
    public void setMontoTotal(BigDecimal montoTotal) {
        this.montoTotal = montoTotal;
    }
    public Venta.TipoVenta getTipoVenta() {
        return tipoVenta;
    }
    public void setTipoVenta(Venta.TipoVenta tipoVenta) {
        this.tipoVenta = tipoVenta;
    }
    public List<DetalleVentaDTO> getDetalles() {
        return detalles;
    }
    public void setDetalles(List<DetalleVentaDTO> detalles) {
        this.detalles = detalles;
    }
    public CreditoDTO getCreditoDTO() {
        return creditoDTO;
    }
    public void setCreditoDTO(CreditoDTO creditoDTO) {
        this.creditoDTO = creditoDTO;
    }

    
}
