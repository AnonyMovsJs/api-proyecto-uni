package com.ronald.proyecto.proyecto_uni.service.impl;

import java.time.LocalDate;
import java.util.List;

import com.ronald.proyecto.proyecto_uni.dto.PagoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Credito;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.Pago;
import com.ronald.proyecto.proyecto_uni.entity.Venta;
import com.ronald.proyecto.proyecto_uni.repository.CreditoRepository;
import com.ronald.proyecto.proyecto_uni.repository.CuotaRepository;
import com.ronald.proyecto.proyecto_uni.repository.PagoRepository;
import com.ronald.proyecto.proyecto_uni.repository.VentaRepository;

import jakarta.transaction.Transactional;

public class PagoServiceImpl {

    private final PagoRepository pagoRepository;
    private final CuotaRepository cuotaRepository;
    private final CreditoRepository creditoRepository;
    private final VentaRepository ventaRepository;
    
    public PagoServiceImpl(PagoRepository pagoRepository,
                     CuotaRepository cuotaRepository,
                     CreditoRepository creditoRepository,
                     VentaRepository ventaRepository) {
        this.pagoRepository = pagoRepository;
        this.cuotaRepository = cuotaRepository;
        this.creditoRepository = creditoRepository;
        this.ventaRepository = ventaRepository;
    }
    
    @Transactional
    public Pago registrarPago(PagoDTO pagoDTO) {
        Cuota cuota = cuotaRepository.findById(pagoDTO.getCuotaId())
            .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));
        
        // Validar que la cuota no esté pagada
        if (cuota.getEstado() == Cuota.EstadoCuota.PAGADO) {
            throw new RuntimeException("La cuota ya está pagada");
        }
        
        // Validar que el monto sea correcto
        if (pagoDTO.getMonto().compareTo(cuota.getMonto()) != 0) {
            throw new RuntimeException("El monto del pago debe ser igual al monto de la cuota");
        }
        
        // Registrar pago
        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(pagoDTO.getMonto());
        pago.setFechaPago(LocalDate.now());
        
        Pago pagoGuardado = pagoRepository.save(pago);
        
        // Actualizar estado de la cuota
        cuota.setEstado(Cuota.EstadoCuota.PAGADO);
        cuotaRepository.save(cuota);
        
        // Verificar si todas las cuotas están pagadas
        Credito credito = cuota.getCredito();
        List<Cuota> cuotas = cuotaRepository.findByCreditoId(credito.getId());
        boolean todasPagadas = cuotas.stream()
            .allMatch(c -> c.getEstado() == Cuota.EstadoCuota.PAGADO);
        
        if (todasPagadas) {
            // Actualizar estado del crédito
            credito.setEstado(Credito.EstadoCredito.PAGADO);
            creditoRepository.save(credito);
            
            // Actualizar estado de la venta
            Venta venta = credito.getVenta();
            venta.setEstado(Venta.EstadoVenta.PAGADO);
            ventaRepository.save(venta);
        }
        
        return pagoGuardado;
    }
    
    public List<Pago> obtenerPagosPorCuota(Long cuotaId) {
        return pagoRepository.findByCuotaId(cuotaId);
    }
    
    public List<Pago> obtenerPagosPorCliente(Long clienteId) {
        return pagoRepository.findByCuotaCreditoVentaClienteId(clienteId);
    }
}
