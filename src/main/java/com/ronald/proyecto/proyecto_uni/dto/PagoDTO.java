package com.ronald.proyecto.proyecto_uni.dto;

import java.math.BigDecimal;

public class PagoDTO {
    private Long cuotaId;
    private BigDecimal monto;
    
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

    
}
