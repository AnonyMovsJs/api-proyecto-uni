package com.ronald.proyecto.proyecto_uni.dto;

import java.math.BigDecimal;

public class CreditoDTO {
    private BigDecimal interes;
    private Integer numeroCuotas;
    
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

    
}
