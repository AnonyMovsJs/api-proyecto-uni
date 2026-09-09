package com.ronald.proyecto.proyecto_uni.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ronald.proyecto.proyecto_uni.entity.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long>{
    @Query("SELECT p FROM Pago p WHERE p.cuota.id = :cuotaId")
    List<Pago> findByCuotaId(@Param("cuotaId") Long cuotaId);

    @Query("SELECT p FROM Pago p WHERE p.cuota.credito.venta.cliente.id = :clienteId")
    List<Pago> findByCuotaCreditoVentaClienteId(@Param("clienteId") Long clienteId);

    @Query("SELECT p FROM Pago p WHERE p.cuota.credito.venta.cliente.id = :clienteId ORDER BY p.fechaPago DESC")
    List<Pago> findByCuotaCreditoVentaClienteIdOrderByFechaPagoDesc(@Param("clienteId") Long clienteId);

    List<Pago> findByEstadoOrderByFechaPagoDesc(String estado);

    List<Pago> findAllByOrderByFechaPagoDesc();
}
