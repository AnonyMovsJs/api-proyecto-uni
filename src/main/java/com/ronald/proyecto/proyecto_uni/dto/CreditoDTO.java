package com.ronald.proyecto.proyecto_uni.dto;

import java.math.BigDecimal;

public class CreditoDTO {
    private BigDecimal interes;
    private Integer numeroCuotas;
    private Integer plazoDias; // Para ventas fiadas en bodega (ej: 15 o 30 días)
    
    public BigDecimal getInteres() {
        return interes;
    }
    public void setInteres(BigDecimal interes) {
        this.interes = interes;
    }
    public Integer getNumeroCuotas() {
        return numeroCuotas;
    }
    public void setNumeroCuotas(Integer numeroCuotas) {
        this.numeroCuotas = numeroCuotas;
    }
    public Integer getPlazoDias() {
        return plazoDias;
    }
    public void setPlazoDias(Integer plazoDias) {
        this.plazoDias = plazoDias;
    }

    
}
