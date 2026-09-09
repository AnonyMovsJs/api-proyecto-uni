package com.ronald.proyecto.proyecto_uni.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ronald.proyecto.proyecto_uni.entity.Notificacion;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByClienteIdOrderByFechaEnvioDesc(Integer clienteId);
    List<Notificacion> findByClienteIdAndLeidaFalseOrderByFechaEnvioDesc(Integer clienteId);
    long countByClienteIdAndLeidaFalse(Integer clienteId);
}
