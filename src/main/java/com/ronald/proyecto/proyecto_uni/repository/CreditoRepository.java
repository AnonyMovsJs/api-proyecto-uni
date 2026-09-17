package com.ronald.proyecto.proyecto_uni.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ronald.proyecto.proyecto_uni.entity.Credito;

public interface CreditoRepository extends JpaRepository<Credito, Long> {

    @Query("SELECT c FROM Credito c WHERE c.venta.id = :ventaId")
    Optional<Credito> findByVentaId(@Param("ventaId") Long ventaId);

    @Query("SELECT c FROM Credito c WHERE c.venta.cliente.id = :clienteId")
    List<Credito> findByVentaClienteId(@Param("clienteId") Long clienteId);
}
