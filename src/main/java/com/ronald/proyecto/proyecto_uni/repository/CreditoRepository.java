package com.ronald.proyecto.proyecto_uni.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ronald.proyecto.proyecto_uni.entity.Credito;

public interface CreditoRepository extends JpaRepository<Credito, Long> {

    Optional<Credito> findByVentaId(Long ventaId);

    List<Credito> findByVentaClienteId(Long clienteId);
}
