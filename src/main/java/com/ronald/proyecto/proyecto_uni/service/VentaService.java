package com.ronald.proyecto.proyecto_uni.service;

import java.util.List;

import com.ronald.proyecto.proyecto_uni.dto.VentaDTO;
import com.ronald.proyecto.proyecto_uni.entity.DetalleVenta;
import com.ronald.proyecto.proyecto_uni.entity.Venta;

public interface VentaService {

    Venta registrarVenta(VentaDTO ventaDTO);

    List<Venta> obtenerVentasPorCliente(Long clienteId);

    Venta obtenerVenta(Long ventaId);

    List<DetalleVenta> obtenerDetallesVenta(Long ventaId);
}
