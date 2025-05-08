package com.ronald.proyecto.proyecto_uni.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.service.CuotaService;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/cuotas")
public class CuotaController {
    private final CuotaService cuotaService;

    public CuotaController(CuotaService cuotaService) {
        this.cuotaService = cuotaService;
    }

    /**
     * GET /api/cuotas/credito/{creditoId}
     * Lista todas las cuotas de un crédito específico.
     */
    @GetMapping("/credito/{creditoId}")
    public ResponseEntity<List<Cuota>> obtenerCuotasPorCredito(@PathVariable Long creditoId) {
        List<Cuota> cuotas = cuotaService.obtenerCuotasPorCredito(creditoId);
        return ResponseEntity.ok(cuotas);
    }

    /**
     * GET /api/cuotas/{cuotaId}
     * Retorna una cuota por su ID.
     */
    @GetMapping("/{cuotaId}")
    public ResponseEntity<Cuota> obtenerCuotaPorId(@PathVariable Long cuotaId) {
        Cuota cuota = cuotaService.obtenerCuotaPorId(cuotaId);
        return ResponseEntity.ok(cuota);
    }
}
