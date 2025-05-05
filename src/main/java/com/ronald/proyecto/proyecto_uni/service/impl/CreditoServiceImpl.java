package com.ronald.proyecto.proyecto_uni.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ronald.proyecto.proyecto_uni.dto.CreditoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Credito;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.Venta;
import com.ronald.proyecto.proyecto_uni.repository.CreditoRepository;
import com.ronald.proyecto.proyecto_uni.repository.CuotaRepository;
import com.ronald.proyecto.proyecto_uni.service.CreditoService;

import jakarta.transaction.Transactional;

@Service
public class CreditoServiceImpl implements CreditoService{

    private final CreditoRepository creditoRepository;
    private final CuotaRepository cuotaRepository;
    

    public CreditoServiceImpl(CreditoRepository creditoRepository, 
                         CuotaRepository cuotaRepository) {
        this.creditoRepository = creditoRepository;
        this.cuotaRepository = cuotaRepository;
    }
    
    @Transactional
    public Credito crearCredito(Venta venta, CreditoDTO creditoDTO) {
        Credito credito = new Credito();
        credito.setVenta(venta);
        
        // Calcular monto total con interés (si hay)
        BigDecimal interes = creditoDTO.getInteres() != null ? creditoDTO.getInteres() : BigDecimal.ZERO;
        credito.setInteres(interes);
        
        BigDecimal montoTotal = venta.getMontoTotal();
        if (interes.compareTo(BigDecimal.ZERO) > 0) {
            // Calcular interés (monto * (1 + interes/100))
            montoTotal = montoTotal.multiply(
                BigDecimal.ONE.add(interes.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP))
            );
        }
        credito.setMontoTotal(montoTotal);
        
        credito.setNumeroCuotas(creditoDTO.getNumeroCuotas());
        credito.setFechaInicio(LocalDate.now());
        credito.setFechaFin(LocalDate.now().plusMonths(creditoDTO.getNumeroCuotas()));
        credito.setEstado(Credito.EstadoCredito.ACTIVO);
        
        Credito creditoGuardado = creditoRepository.save(credito);
        
        // Generar cuotas
        generarCuotas(creditoGuardado);
        
        return creditoGuardado;
    }
    
    private void generarCuotas(Credito credito) {
        // Calcular monto por cuota
        BigDecimal montoPorCuota = credito.getMontoTotal()
            .divide(new BigDecimal(credito.getNumeroCuotas()), 2, RoundingMode.HALF_UP);
        
        LocalDate fechaVencimiento = credito.getFechaInicio();
        
        for (int i = 1; i <= credito.getNumeroCuotas(); i++) {
            Cuota cuota = new Cuota();
            cuota.setCredito(credito);
            cuota.setNumeroCuota(i);
            cuota.setMonto(montoPorCuota);
            
            // Agregar un mes a la fecha de vencimiento
            fechaVencimiento = fechaVencimiento.plusMonths(1);
            cuota.setFechaVencimiento(fechaVencimiento);
            
            cuota.setEstado(Cuota.EstadoCuota.PENDIENTE);
            
            cuotaRepository.save(cuota);
        }
    }
    
    public List<Credito> obtenerCreditosPorCliente(Long clienteId) {
        return creditoRepository.findByVentaClienteId(clienteId);
    }

    
    public Credito obtenerCreditoPorVenta(Long ventaId) {
        return creditoRepository.findByVentaId(ventaId)
            .orElseThrow(() -> new RuntimeException("Crédito no encontrado para esta venta"));
    }
    
    public List<Cuota> obtenerCuotasPorCredito(Long creditoId) {
        return cuotaRepository.findByCreditoId(creditoId);
    }
    
    // Método para verificar cuotas vencidas (se puede programar para ejecutar diariamente)
    @Scheduled(cron = "0 0 0 * * ?") // Ejecutar todos los días a medianoche
    public void verificarCuotasVencidas() {
        LocalDate hoy = LocalDate.now();
        List<Cuota> cuotasVencidas = cuotaRepository.findByEstadoAndFechaVencimientoBefore(
            Cuota.EstadoCuota.PENDIENTE, hoy);
        
        for (Cuota cuota : cuotasVencidas) {
            cuota.setEstado(Cuota.EstadoCuota.VENCIDO);
            cuotaRepository.save(cuota);
        }
    }
}
