package com.ronald.proyecto.proyecto_uni.service;

import java.util.List;

import com.ronald.proyecto.proyecto_uni.dto.PagoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Pago;

public interface PagoService {

    Pago registrarPago(PagoDTO pagoDTO);

    Pago registrarPagoConComprobante(Long cuotaId, org.springframework.web.multipart.MultipartFile comprobante);

    Pago registrarPagoConComprobante(Long cuotaId, org.springframework.web.multipart.MultipartFile comprobante, String metodoPago);

    Pago validarPago(Long pagoId, String nuevoEstado, String motivoRechazo);

    List<Pago> obtenerPagosPorCuota(Long cuotaId);

    List<Pago> obtenerPagosPorCliente(Long clienteId);

    List<Pago> listarPagosPendientes();

    List<Pago> listarTodosLosPagos();

    Pago registrarAbonoFiado(Long clienteId, java.math.BigDecimal monto, org.springframework.web.multipart.MultipartFile comprobante, String tipoAbono);
}
