package com.ronald.proyecto.proyecto_uni.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;


@Entity
@Table(name = "venta")
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User cliente;

    @Column(nullable = true)
    private String descripcion;

    @Column(nullable = false)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVenta tipoVenta;

    @Column(nullable = false)
    private LocalDate fechaVenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoVenta estado;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("venta-detalles")
    private List<DetalleVenta> detalles = new ArrayList<>();

    @OneToOne(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("venta-credito")
    private Credito credito;

    public enum TipoVenta {
        CONTADO, CREDITO, FIADO
    }

    public enum EstadoVenta {
        PENDIENTE, PAGADO, VENCIDO
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getCliente() {
        return cliente;
    }

    public void setCliente(User cliente) {
        this.cliente = cliente;
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

    public TipoVenta getTipoVenta() {
        return tipoVenta;
    }

    public void setTipoVenta(TipoVenta tipoVenta) {
        this.tipoVenta = tipoVenta;
    }

    public LocalDate getFechaVenta() {
        return fechaVenta;
    }

    public void setFechaVenta(LocalDate fechaVenta) {
        this.fechaVenta = fechaVenta;
    }

    public EstadoVenta getEstado() {
        return estado;
    }

    public void setEstado(EstadoVenta estado) {
        this.estado = estado;
    }

    public List<DetalleVenta> getDetalles() {
        return detalles;
    }

    public void setDetalles(List<DetalleVenta> detalles) {
        this.detalles = detalles;
    }

    public Credito getCredito() {
        return credito;
    }

    public void setCredito(Credito credito) {
        this.credito = credito;
    }

    @jakarta.persistence.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal getTotalPagado() {
        if (estado == EstadoVenta.PAGADO || tipoVenta == TipoVenta.CONTADO) {
            return (montoTotal != null) ? montoTotal : BigDecimal.ZERO;
        }
        if (credito == null || credito.getCuotas() == null || credito.getCuotas().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal pendiente = BigDecimal.ZERO;
        for (Cuota c : credito.getCuotas()) {
            if (c.getEstado() != Cuota.EstadoCuota.PAGADO) {
                pendiente = pendiente.add(c.getMonto());
            }
        }
        BigDecimal total = (credito.getMontoTotal() != null) ? credito.getMontoTotal() : montoTotal;
        if (total == null) return BigDecimal.ZERO;
        BigDecimal pagado = total.subtract(pendiente);
        return pagado.compareTo(BigDecimal.ZERO) > 0 ? pagado : BigDecimal.ZERO;
    }

    @jakarta.persistence.Transient
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public BigDecimal getSaldoPendiente() {
        if (estado == EstadoVenta.PAGADO || tipoVenta == TipoVenta.CONTADO) {
            return BigDecimal.ZERO;
        }
        if (credito == null || credito.getCuotas() == null || credito.getCuotas().isEmpty()) {
            return (montoTotal != null) ? montoTotal : BigDecimal.ZERO;
        }
        BigDecimal pendiente = BigDecimal.ZERO;
        for (Cuota c : credito.getCuotas()) {
            if (c.getEstado() != Cuota.EstadoCuota.PAGADO) {
                pendiente = pendiente.add(c.getMonto());
            }
        }
        return pendiente;
    }
}
