package com.ronald.proyecto.proyecto_uni.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "pago")
public class Pago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cuota_id", nullable = false)
    @JsonBackReference("cuota-pagos")
    private Cuota cuota;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(nullable = false)
    private LocalDate fechaPago;

    @Column(length = 30)
    private String metodoPago; // EJ: YAPE, EFECTIVO, TRANSFERENCIA

    @Column(length = 30)
    private String estado; // PENDIENTE, APROBADO, RECHAZADO

    @Column(length = 500)
    private String comprobanteUrl;

    @Column(length = 150)
    private String publicIdCloudinary;

    @Column
    private LocalDate fechaValidacion;

    @Column(length = 255)
    private String motivoRechazo;

    @Column(length = 30)
    private String tipoAbono; // 'FIADO', 'CREDITO', o 'TODO'

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cuota getCuota() {
        return cuota;
    }

    public void setCuota(Cuota cuota) {
        this.cuota = cuota;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(String metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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

    public LocalDate getFechaValidacion() {
        return fechaValidacion;
    }

    public void setFechaValidacion(LocalDate fechaValidacion) {
        this.fechaValidacion = fechaValidacion;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public String getTipoAbono() {
        return tipoAbono;
    }

    public void setTipoAbono(String tipoAbono) {
        this.tipoAbono = tipoAbono;
    }

    // Datos contextuales deserializados en JSON para el frontend
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getCuotaId() {
        return cuota != null ? cuota.getId() : null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer getNumeroCuota() {
        return cuota != null ? cuota.getNumeroCuota() : null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getCreditoId() {
        return (cuota != null && cuota.getCredito() != null) ? cuota.getCredito().getId() : null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer getTotalCuotas() {
        return (cuota != null && cuota.getCredito() != null) ? cuota.getCredito().getNumeroCuotas() : null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getVentaId() {
        return (cuota != null && cuota.getCredito() != null && cuota.getCredito().getVenta() != null)
                ? cuota.getCredito().getVenta().getId() : null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Integer getClienteId() {
        return (cuota != null && cuota.getCredito() != null && cuota.getCredito().getVenta() != null && cuota.getCredito().getVenta().getCliente() != null)
                ? cuota.getCredito().getVenta().getCliente().getId() : null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getClienteNombre() {
        if (cuota != null && cuota.getCredito() != null && cuota.getCredito().getVenta() != null && cuota.getCredito().getVenta().getCliente() != null) {
            User c = cuota.getCredito().getVenta().getCliente();
            return c.getName() + " " + c.getLastname();
        }
        return null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getClienteDni() {
        if (cuota != null && cuota.getCredito() != null && cuota.getCredito().getVenta() != null && cuota.getCredito().getVenta().getCliente() != null) {
            return cuota.getCredito().getVenta().getCliente().getDni();
        }
        return null;
    }

    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getTipoVenta() {
        if (cuota != null && cuota.getCredito() != null && cuota.getCredito().getVenta() != null && cuota.getCredito().getVenta().getTipoVenta() != null) {
            return cuota.getCredito().getVenta().getTipoVenta().name();
        }
        return null;
    }
}
