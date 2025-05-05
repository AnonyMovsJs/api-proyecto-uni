package com.ronald.proyecto.proyecto_uni.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ronald.proyecto.proyecto_uni.entity.Cuota;

public interface CuotaRepository extends JpaRepository<Cuota, Long>{
    List<Cuota> findByCreditoId(Long creditoId);
    List<Cuota> findByEstadoAndFechaVencimientoBefore(Cuota.EstadoCuota estado, LocalDate fecha);
}
