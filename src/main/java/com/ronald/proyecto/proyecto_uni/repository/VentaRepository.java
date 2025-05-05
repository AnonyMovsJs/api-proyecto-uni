package com.ronald.proyecto.proyecto_uni.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ronald.proyecto.proyecto_uni.entity.Venta;

public interface VentaRepository extends JpaRepository<Venta, Long>{
    List<Venta> findByClienteId(Long clienteId);
}
