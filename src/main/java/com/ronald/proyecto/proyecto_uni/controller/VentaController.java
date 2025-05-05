package com.ronald.proyecto.proyecto_uni.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ronald.proyecto.proyecto_uni.service.VentaService;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.ronald.proyecto.proyecto_uni.dto.VentaDTO;
import com.ronald.proyecto.proyecto_uni.entity.DetalleVenta;
import com.ronald.proyecto.proyecto_uni.entity.Venta;


@RestController
@RequestMapping("/api/ventas")
public class VentaController {
    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping
    public ResponseEntity<Venta> crearVenta(
            @RequestBody VentaDTO ventaDTO,
            Authentication authentication) {

        Venta venta = ventaService.registrarVenta(ventaDTO);
        return new ResponseEntity<>(venta, HttpStatus.CREATED);
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Venta>> obtenerVentasPorCliente(@PathVariable Long clienteId) {
        List<Venta> ventas = ventaService.obtenerVentasPorCliente(clienteId);
        return ResponseEntity.ok(ventas);
    }

    @GetMapping("/{ventaId}")
    public ResponseEntity<Venta> obtenerVenta(@PathVariable Long ventaId) {
        Venta venta = ventaService.obtenerVenta(ventaId);
        return ResponseEntity.ok(venta);
    }

    @GetMapping("/{ventaId}/detalles")
    public ResponseEntity<List<DetalleVenta>> obtenerDetallesVenta(@PathVariable Long ventaId) {
        List<DetalleVenta> detalles = ventaService.obtenerDetallesVenta(ventaId);
        return ResponseEntity.ok(detalles);
    }
}
