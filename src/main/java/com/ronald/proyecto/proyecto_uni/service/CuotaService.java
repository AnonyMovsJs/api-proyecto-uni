package com.ronald.proyecto.proyecto_uni.service;

import java.util.List;

import com.ronald.proyecto.proyecto_uni.entity.Cuota;

public interface CuotaService {

     List<Cuota> obtenerCuotasPorCredito(Long creditoId);

    Cuota obtenerCuotaPorId(Long cuotaId);
}
