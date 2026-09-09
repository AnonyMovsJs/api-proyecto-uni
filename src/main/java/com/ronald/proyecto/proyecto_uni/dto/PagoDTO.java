package com.ronald.proyecto.proyecto_uni.dto;

import java.math.BigDecimal;

public class PagoDTO {
    private Long cuotaId;
    private BigDecimal monto;
    private String metodoPago;
    private String comprobanteUrl;
    private String publicIdCloudinary;
    
    public Long getCuotaId() {
        return cuotaId;
    }
    public void setCuotaId(Long cuotaId) {
        this.cuotaId = cuotaId;
    }
    public BigDecimal getMonto() {
        return monto;
    }
    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }
    public String getMetodoPago() {
        return metodoPago;
    }
    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }
    public String getComprobanteUrl() {
        return comprobanteUrl;
    }
    public void setComprobanteUrl(String comprobanteUrl) {
        this.comprobanteUrl = comprobanteUrl;
    }
    public String getPublicIdCloudinary() {
        return publicIdCloudinary;
    }
    public void setPublicIdCloudinary(String publicIdCloudinary) {
        this.publicIdCloudinary = publicIdCloudinary;
    }

    
}
