package com.ronald.proyecto.proyecto_uni.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ronald.proyecto.proyecto_uni.entity.Cuota;

public class CuotaDTO {
    private Long id;
    private Integer numeroCuota;
    private BigDecimal monto;
    private LocalDate fechaVencimiento;
    private Cuota.EstadoCuota estado;
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Integer getNumeroCuota() {
        return numeroCuota;
    }
    public void setNumeroCuota(Integer numeroCuota) {
        this.numeroCuota = numeroCuota;
    }
    public BigDecimal getMonto() {
        return monto;
    }
    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }
    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }
    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }
    public Cuota.EstadoCuota getEstado() {
        return estado;
    }
    public void setEstado(Cuota.EstadoCuota estado) {
        this.estado = estado;
    }

    
}
