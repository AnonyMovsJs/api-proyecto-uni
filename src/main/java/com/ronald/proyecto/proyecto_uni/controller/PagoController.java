package com.ronald.proyecto.proyecto_uni.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ronald.proyecto.proyecto_uni.dto.PagoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Pago;
import com.ronald.proyecto.proyecto_uni.service.PagoService;
import com.ronald.proyecto.proyecto_uni.service.TelegramNotificationService;

@CrossOrigin(origins = "http://localhost:4200/")
@RestController
@RequestMapping("/api/pagos")
public class PagoController {
    private final PagoService pagoService;
    private final TelegramNotificationService telegramNotificationService;

    public PagoController(PagoService pagoService, TelegramNotificationService telegramNotificationService) {
        this.pagoService = pagoService;
        this.telegramNotificationService = telegramNotificationService;
    }

    @PostMapping
    public ResponseEntity<Pago> registrarPago(@RequestBody PagoDTO pagoDTO) {
        Pago pago = pagoService.registrarPago(pagoDTO);
        return new ResponseEntity<>(pago, HttpStatus.CREATED);
    }

    // Subir comprobante Yape (Cliente)
    @PostMapping("/yape")
    public ResponseEntity<?> registrarPagoYape(
            @RequestParam("cuotaId") Long cuotaId,
            @RequestParam("comprobante") MultipartFile comprobante) {
        try {
            Pago pago = pagoService.registrarPagoConComprobante(cuotaId, comprobante);
            return new ResponseEntity<>(pago, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Subir comprobante Yape para abono a cuenta corriente / Fiado (Cliente)
    @PostMapping("/yape/fiado")
    public ResponseEntity<?> registrarAbonoFiado(
            @RequestParam("clienteId") Long clienteId,
            @RequestParam("monto") java.math.BigDecimal monto,
            @RequestParam("comprobante") MultipartFile comprobante,
            @RequestParam(value = "tipoAbono", required = false, defaultValue = "FIADO") String tipoAbono) {
        try {
            Pago pago = pagoService.registrarAbonoFiado(clienteId, monto, comprobante, tipoAbono);
            return new ResponseEntity<>(pago, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Validar comprobante (Admin)
    @PatchMapping("/{pagoId}/validar")
    public ResponseEntity<?> validarPago(
            @PathVariable Long pagoId,
            @RequestBody Map<String, String> body) {
        try {
            String nuevoEstado = body.get("estado"); // APROBADO o RECHAZADO
            String motivoRechazo = body.get("motivoRechazo");
            Pago pago = pagoService.validarPago(pagoId, nuevoEstado, motivoRechazo);
            return ResponseEntity.ok(pago);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<Pago>> listarPagosPendientes() {
        return ResponseEntity.ok(pagoService.listarPagosPendientes());
    }

    @GetMapping
    public ResponseEntity<List<Pago>> listarTodosLosPagos() {
        return ResponseEntity.ok(pagoService.listarTodosLosPagos());
    }

    @GetMapping("/cuota/{cuotaId}")
    public ResponseEntity<List<Pago>> obtenerPagosPorCuota(@PathVariable Long cuotaId) {
        List<Pago> pagos = pagoService.obtenerPagosPorCuota(cuotaId);
        return ResponseEntity.ok(pagos);
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Pago>> obtenerPagosPorCliente(@PathVariable Long clienteId) {
        List<Pago> pagos = pagoService.obtenerPagosPorCliente(clienteId);
        return ResponseEntity.ok(pagos);
    }

    // Obtener y cambiar el switch de notificaciones de Telegram
    @GetMapping("/config/telegram")
    public ResponseEntity<Map<String, Object>> getTelegramConfig() {
        return ResponseEntity.ok(Map.of(
                "enabled", telegramNotificationService.isNotificationsEnabled(),
                "configured", telegramNotificationService.isConfigured()
        ));
    }

    @PutMapping("/config/telegram")
    public ResponseEntity<Map<String, Object>> updateTelegramConfig(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.getOrDefault("enabled", true);
        telegramNotificationService.setNotificationsEnabled(enabled);
        return ResponseEntity.ok(Map.of(
                "enabled", telegramNotificationService.isNotificationsEnabled(),
                "message", "Configuración de Telegram actualizada con éxito"
        ));
    }
}
