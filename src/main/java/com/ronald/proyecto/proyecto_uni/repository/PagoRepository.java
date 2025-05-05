package com.ronald.proyecto.proyecto_uni.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ronald.proyecto.proyecto_uni.entity.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long>{
    List<Pago> findByCuotaId(Long cuotaId);

    List<Pago> findByCuotaCreditoVentaClienteId(Long clienteId);
}
