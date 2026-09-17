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
        
        boolean esFiado = (venta.getTipoVenta() == Venta.TipoVenta.FIADO);

        // En fiado no hay interés por defecto; en crédito se respeta el DTO
        BigDecimal interes = (esFiado || creditoDTO.getInteres() == null) ? BigDecimal.ZERO : creditoDTO.getInteres();
        credito.setInteres(interes);
        
        BigDecimal montoTotal = venta.getMontoTotal();
        if (interes.compareTo(BigDecimal.ZERO) > 0) {
            // Calcular interés (monto * (1 + interes/100))
            montoTotal = montoTotal.multiply(
                BigDecimal.ONE.add(interes.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP))
            );
        }
        credito.setMontoTotal(montoTotal);
        
        int cuotas = (esFiado || creditoDTO.getNumeroCuotas() == null || creditoDTO.getNumeroCuotas() < 1) ? 1 : creditoDTO.getNumeroCuotas();
        credito.setNumeroCuotas(cuotas);
        credito.setFechaInicio(LocalDate.now());

        if (esFiado) {
            int dias = (creditoDTO.getPlazoDias() != null && creditoDTO.getPlazoDias() > 0) ? creditoDTO.getPlazoDias() : 30;
            credito.setFechaFin(LocalDate.now().plusDays(dias));
        } else {
            credito.setFechaFin(LocalDate.now().plusMonths(cuotas));
        }
        credito.setEstado(Credito.EstadoCredito.ACTIVO);
        
        Credito creditoGuardado = creditoRepository.save(credito);
        
        // Generar cuotas (1 para fiado con vencimiento = fechaFin, o N cuotas mensuales para crédito tradicional)
        generarCuotas(creditoGuardado, esFiado);
        
        return creditoGuardado;
    }
    
    private void generarCuotas(Credito credito, boolean esFiado) {
        if (esFiado) {
            Cuota cuota = new Cuota();
            cuota.setCredito(credito);
            cuota.setNumeroCuota(1);
            cuota.setMonto(credito.getMontoTotal());
            cuota.setFechaVencimiento(credito.getFechaFin());
            cuota.setEstado(Cuota.EstadoCuota.PENDIENTE);
            cuotaRepository.save(cuota);
            return;
        }

        // Calcular monto por cuota para crédito estándar
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
        List<Cuota> cuotas = cuotaRepository.findByCreditoId(creditoId);

        // Log para depuración
        System.out.println("Obteniendo cuotas para crédito ID: " + creditoId);
        System.out.println("Número de cuotas encontradas: " + cuotas.size());

        for (Cuota cuota : cuotas) {
            System.out.println("Cuota #" + cuota.getNumeroCuota() +
                    " - Monto: " + cuota.getMonto() +
                    " - Estado: " + cuota.getEstado() +
                    " - Fecha vencimiento: " + cuota.getFechaVencimiento());
        }

        return cuotas;
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

    @Override
    @Transactional
    public void agregarProductosACuenta(Venta ventaExistente, BigDecimal montoAdicional, String nuevaFechaVencimiento) {
        Credito credito = creditoRepository.findByVentaId(ventaExistente.getId())
            .orElseThrow(() -> new RuntimeException("Crédito no encontrado para la venta ID: " + ventaExistente.getId()));

        // Actualizar monto total del crédito
        BigDecimal nuevoTotalCredito = credito.getMontoTotal().add(montoAdicional);
        credito.setMontoTotal(nuevoTotalCredito);

        // Si se especificó una nueva fecha de vencimiento, actualizar fecha fin
        LocalDate fechaVencParsed = null;
        if (nuevaFechaVencimiento != null && !nuevaFechaVencimiento.isBlank()) {
            try {
                fechaVencParsed = LocalDate.parse(nuevaFechaVencimiento.trim());
                credito.setFechaFin(fechaVencParsed);
            } catch (Exception e) {
                // Si el parseo falla, mantener la fecha actual
            }
        }

        creditoRepository.save(credito);

        // Actualizar las cuotas pendientes: sumar el monto adicional a la última cuota activa o pendiente
        List<Cuota> cuotas = cuotaRepository.findByCreditoId(credito.getId());
        Cuota cuotaAjustar = cuotas.stream()
            .filter(c -> c.getEstado() != Cuota.EstadoCuota.PAGADO)
            .reduce((first, second) -> second) // tomar la última no pagada
            .orElse(null);

        if (cuotaAjustar != null) {
            cuotaAjustar.setMonto(cuotaAjustar.getMonto().add(montoAdicional));
            if (fechaVencParsed != null) {
                cuotaAjustar.setFechaVencimiento(fechaVencParsed);
            }
            // Si estaba vencida pero se extendió la fecha a futuro, resetear a PENDIENTE
            if (cuotaAjustar.getEstado() == Cuota.EstadoCuota.VENCIDO && cuotaAjustar.getFechaVencimiento().isAfter(LocalDate.now())) {
                cuotaAjustar.setEstado(Cuota.EstadoCuota.PENDIENTE);
            }
            cuotaRepository.save(cuotaAjustar);
        } else {
            // Si por alguna razón todas estaban pagadas, crear una nueva cuota con el saldo agregado
            Cuota nuevaCuota = new Cuota();
            nuevaCuota.setCredito(credito);
            nuevaCuota.setNumeroCuota(cuotas.size() + 1);
            nuevaCuota.setMonto(montoAdicional);
            nuevaCuota.setFechaVencimiento(fechaVencParsed != null ? fechaVencParsed : credito.getFechaFin());
            nuevaCuota.setEstado(Cuota.EstadoCuota.PENDIENTE);
            cuotaRepository.save(nuevaCuota);
        }
    }

    @Override
    @Transactional
    public Cuota actualizarFechaVencimientoCuota(Long cuotaId, LocalDate nuevaFecha) {
        if (nuevaFecha == null) {
            throw new IllegalArgumentException("La nueva fecha de vencimiento no puede ser nula");
        }
        Cuota cuota = cuotaRepository.findById(cuotaId)
            .orElseThrow(() -> new RuntimeException("Cuota no encontrada con ID: " + cuotaId));

        cuota.setFechaVencimiento(nuevaFecha);
        if (cuota.getEstado() == Cuota.EstadoCuota.VENCIDO && nuevaFecha.isAfter(LocalDate.now())) {
            cuota.setEstado(Cuota.EstadoCuota.PENDIENTE);
        }
        Cuota guardada = cuotaRepository.save(cuota);

        // Actualizar también la fecha fin del crédito si esta cuota supera la actual fecha fin
        Credito credito = cuota.getCredito();
        if (credito != null && (credito.getFechaFin() == null || nuevaFecha.isAfter(credito.getFechaFin()))) {
            credito.setFechaFin(nuevaFecha);
            creditoRepository.save(credito);
        }

        return guardada;
    }
}
