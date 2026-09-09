package com.ronald.proyecto.proyecto_uni.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class NotificacionDTO {
    private Integer clienteId;
    private String titulo;
    private String mensaje;
    private BigDecimal montoDeuda;
    private Integer diasRetraso;
    private LocalDateTime fechaVencimiento;
    private String tipo;

    public Integer getClienteId() {
        return clienteId;
    }
    public void setClienteId(Integer clienteId) {
        this.clienteId = clienteId;
    }
    public String getTitulo() {
        return titulo;
    }
    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }
    public String getMensaje() {
        return mensaje;
    }
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
    public BigDecimal getMontoDeuda() {
        return montoDeuda;
    }
    public void setMontoDeuda(BigDecimal montoDeuda) {
        this.montoDeuda = montoDeuda;
    }
    public Integer getDiasRetraso() {
        return diasRetraso;
    }
    public void setDiasRetraso(Integer diasRetraso) {
        this.diasRetraso = diasRetraso;
    }
    public LocalDateTime getFechaVencimiento() {
        return fechaVencimiento;
    }
    public void setFechaVencimiento(LocalDateTime fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }
    public String getTipo() {
        return tipo;
    }
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
