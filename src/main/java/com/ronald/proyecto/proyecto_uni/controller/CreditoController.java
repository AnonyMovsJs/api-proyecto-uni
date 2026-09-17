package com.ronald.proyecto.proyecto_uni.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ronald.proyecto.proyecto_uni.service.CreditoService;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ronald.proyecto.proyecto_uni.entity.Credito;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;

@CrossOrigin(origins = "http://localhost:4200/")
@RestController
@RequestMapping("/api/creditos")
public class CreditoController {
    private final CreditoService creditoService;

    public CreditoController(CreditoService creditoService) {
        this.creditoService = creditoService;
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Credito>> obtenerCreditosPorCliente(@PathVariable Long clienteId) {
        List<Credito> creditos = creditoService.obtenerCreditosPorCliente(clienteId);
        return ResponseEntity.ok(creditos);
    }

    @GetMapping("/venta/{ventaId}")
    public ResponseEntity<Credito> obtenerCreditoPorVenta(@PathVariable Long ventaId) {
        Credito credito = creditoService.obtenerCreditoPorVenta(ventaId);
        return ResponseEntity.ok(credito);
    }

    @GetMapping("/{creditoId}/cuotas")
    public ResponseEntity<List<Cuota>> obtenerCuotasPorCredito(@PathVariable Long creditoId) {
        List<Cuota> cuotas = creditoService.obtenerCuotasPorCredito(creditoId);
        return ResponseEntity.ok(cuotas);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/cuota/{cuotaId}/fecha-vencimiento")
    public ResponseEntity<?> actualizarFechaVencimiento(
            @PathVariable Long cuotaId,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body) {
        try {
            String fechaStr = body.get("fechaVencimiento");
            if (fechaStr == null || fechaStr.isBlank()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "Debe proporcionar una fecha de vencimiento válida"));
            }
            java.time.LocalDate nuevaFecha = java.time.LocalDate.parse(fechaStr.trim());
            Cuota cuota = creditoService.actualizarFechaVencimientoCuota(cuotaId, nuevaFecha);
            return ResponseEntity.ok(cuota);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
