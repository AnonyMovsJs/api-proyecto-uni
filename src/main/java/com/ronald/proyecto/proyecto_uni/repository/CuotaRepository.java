package com.ronald.proyecto.proyecto_uni.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.User;

public interface CuotaRepository extends JpaRepository<Cuota, Long>{
    List<Cuota> findByCreditoId(Long creditoId);

    List<Cuota> findByEstadoAndFechaVencimientoBefore(Cuota.EstadoCuota estado, LocalDate fecha);

    // Añade este método a CuotaRepository.java
    @Query("SELECT DISTINCT c.credito.venta.cliente FROM Cuota c WHERE c.estado = 'VENCIDO'")
    List<User> findClientesConCuotasVencidas();

    // Añadir estos métodos al CuotaRepository
    @Query("SELECT c FROM Cuota c WHERE c.estado = 'VENCIDO' AND c.fechaVencimiento = CURRENT_DATE")
    List<Cuota> findCuotasVencidasHoy();

    @Query("SELECT c FROM Cuota c WHERE c.credito.venta.cliente.id = :clienteId ORDER BY c.fechaVencimiento")
    List<Cuota> findCuotasByClienteIdOrdered(@Param("clienteId") Long clienteId);

    @Query("SELECT c FROM Cuota c WHERE c.credito.venta.cliente.id = :clienteId AND c.estado = 'VENCIDO'")
    List<Cuota> findCuotasVencidasByClienteId(@Param("clienteId") Integer clienteId);

}
