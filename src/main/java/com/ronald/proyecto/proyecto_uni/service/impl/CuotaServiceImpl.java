package com.ronald.proyecto.proyecto_uni.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.repository.CuotaRepository;
import com.ronald.proyecto.proyecto_uni.service.CuotaService;

import java.util.List;

@Service
public class CuotaServiceImpl implements CuotaService {
    private final CuotaRepository cuotaRepository;

    public CuotaServiceImpl(CuotaRepository cuotaRepository) {
        this.cuotaRepository = cuotaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cuota> obtenerCuotasPorCredito(Long creditoId) {
        return cuotaRepository.findByCreditoId(creditoId);
    }

    @Override
    @Transactional(readOnly = true)
    public Cuota obtenerCuotaPorId(Long cuotaId) {
        return cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada, id=" + cuotaId));
    }
}
