package com.ronald.proyecto.proyecto_uni.service;

import java.util.List;

import com.ronald.proyecto.proyecto_uni.dto.PagoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Pago;

public interface PagoService {

    Pago registrarPago(PagoDTO pagoDTO);

    List<Pago> obtenerPagosPorCuota(Long cuotaId);

    List<Pago> obtenerPagosPorCliente(Long clienteId);
}
