package com.ronald.proyecto.proyecto_uni.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ronald.proyecto.proyecto_uni.dto.DetalleVentaDTO;
import com.ronald.proyecto.proyecto_uni.dto.VentaDTO;
import com.ronald.proyecto.proyecto_uni.entity.DetalleVenta;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.entity.Venta;
import com.ronald.proyecto.proyecto_uni.repository.DetalleVentaRepository;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.repository.VentaRepository;
import com.ronald.proyecto.proyecto_uni.service.CreditoService;
import com.ronald.proyecto.proyecto_uni.service.VentaService;

import jakarta.transaction.Transactional;

@Service
public class VentaServiceImpl implements VentaService{
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final CreditoService creditoService;
    private final UserRepository userRepository;
    
    public VentaServiceImpl(VentaRepository ventaRepository, 
                       DetalleVentaRepository detalleVentaRepository,
                       CreditoService creditoService,
                       UserRepository userRepository) {
        this.ventaRepository = ventaRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.creditoService = creditoService;
        this.userRepository = userRepository;
    }
    
    @Transactional
    public Venta registrarVenta(VentaDTO ventaDTO) {
        // Obtener cliente y admin
        User cliente = userRepository.findById(ventaDTO.getClienteId().intValue())
            .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        
        
        // Crear venta
        Venta venta = new Venta();
        venta.setCliente(cliente);
        venta.setDescripcion(ventaDTO.getDescripcion());
        venta.setMontoTotal(ventaDTO.getMontoTotal());
        venta.setTipoVenta(ventaDTO.getTipoVenta());
        venta.setFechaVenta(LocalDate.now());
        venta.setEstado(Venta.EstadoVenta.PENDIENTE);
        
        // Guardar venta
        Venta ventaGuardada = ventaRepository.save(venta);
        
        // Guardar detalles
        for (DetalleVentaDTO detalleDTO : ventaDTO.getDetalles()) {
            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(ventaGuardada);
            detalle.setNombreProducto(detalleDTO.getNombreProducto());
            detalle.setCantidad(detalleDTO.getCantidad());
            detalle.setPrecioUnitario(detalleDTO.getPrecioUnitario());
            detalle.setSubtotal(detalleDTO.getPrecioUnitario().multiply(new BigDecimal(detalleDTO.getCantidad())));
            
            detalleVentaRepository.save(detalle);
        }
        
        // Si es venta a crédito, crear crédito
        if (ventaDTO.getTipoVenta() == Venta.TipoVenta.CREDITO) {
            creditoService.crearCredito(ventaGuardada, ventaDTO.getCreditoDTO());
        } else {
            // Si es al contado, marcar como pagado
            ventaGuardada.setEstado(Venta.EstadoVenta.PAGADO);
            ventaRepository.save(ventaGuardada);
        }
        
        return ventaGuardada;
    }
    
    public List<Venta> obtenerVentasPorCliente(Long clienteId) {
        return ventaRepository.findByClienteId(clienteId);
    }
    
    
    public Venta obtenerVenta(Long ventaId) {
        return ventaRepository.findById(ventaId)
            .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
    }
    
    public List<DetalleVenta> obtenerDetallesVenta(Long ventaId) {
        return detalleVentaRepository.findByVentaId(ventaId);
    }
}
