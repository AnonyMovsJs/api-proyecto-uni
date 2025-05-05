package com.ronald.proyecto.proyecto_uni.service;

import java.util.List;

import com.ronald.proyecto.proyecto_uni.dto.CreditoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Credito;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.Venta;

public interface CreditoService {

    Credito crearCredito(Venta venta, CreditoDTO creditoDTO);

    List<Credito> obtenerCreditosPorCliente(Long clienteId);

    Credito obtenerCreditoPorVenta(Long ventaId);

    List<Cuota> obtenerCuotasPorCredito(Long creditoId);

    void verificarCuotasVencidas();
}
