package com.ronald.proyecto.proyecto_uni.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ronald.proyecto.proyecto_uni.service.PagoService;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.ronald.proyecto.proyecto_uni.dto.PagoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Pago;

@CrossOrigin(origins = "http://localhost:4200/")
@RestController
@RequestMapping("/api/pagos")
public class PagoController {
    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping
    public ResponseEntity<Pago> registrarPago(@RequestBody PagoDTO pagoDTO) {
        Pago pago = pagoService.registrarPago(pagoDTO);
        return new ResponseEntity<>(pago, HttpStatus.CREATED);
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
}
