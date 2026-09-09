package com.ronald.proyecto.proyecto_uni.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ronald.proyecto.proyecto_uni.dto.PagoDTO;
import com.ronald.proyecto.proyecto_uni.entity.Credito;
import com.ronald.proyecto.proyecto_uni.entity.Cuota;
import com.ronald.proyecto.proyecto_uni.entity.Pago;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.entity.Venta;
import com.ronald.proyecto.proyecto_uni.repository.CreditoRepository;
import com.ronald.proyecto.proyecto_uni.repository.CuotaRepository;
import com.ronald.proyecto.proyecto_uni.repository.PagoRepository;
import com.ronald.proyecto.proyecto_uni.repository.VentaRepository;
import com.ronald.proyecto.proyecto_uni.service.CloudinaryService;
import com.ronald.proyecto.proyecto_uni.service.PagoService;
import com.ronald.proyecto.proyecto_uni.service.TelegramNotificationService;

import jakarta.transaction.Transactional;

@Service
public class PagoServiceImpl implements PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoServiceImpl.class);

    private final PagoRepository pagoRepository;
    private final CuotaRepository cuotaRepository;
    private final CreditoRepository creditoRepository;
    private final VentaRepository ventaRepository;
    private final CloudinaryService cloudinaryService;
    private final TelegramNotificationService telegramNotificationService;

    public PagoServiceImpl(
            PagoRepository pagoRepository,
            CuotaRepository cuotaRepository,
            CreditoRepository creditoRepository,
            VentaRepository ventaRepository,
            CloudinaryService cloudinaryService,
            TelegramNotificationService telegramNotificationService) {
        this.pagoRepository = pagoRepository;
        this.cuotaRepository = cuotaRepository;
        this.creditoRepository = creditoRepository;
        this.ventaRepository = ventaRepository;
        this.cloudinaryService = cloudinaryService;
        this.telegramNotificationService = telegramNotificationService;
    }

    @Override
    @Transactional
    public Pago registrarPago(PagoDTO pagoDTO) {
        Cuota cuota = cuotaRepository.findById(pagoDTO.getCuotaId())
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        if (cuota.getEstado() == Cuota.EstadoCuota.PAGADO) {
            throw new RuntimeException("La cuota ya está pagada");
        }

        if (pagoDTO.getMonto().compareTo(cuota.getMonto()) != 0) {
            throw new RuntimeException("El monto del pago debe ser igual al monto de la cuota");
        }

        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(pagoDTO.getMonto());
        pago.setFechaPago(LocalDate.now());
        pago.setMetodoPago(pagoDTO.getMetodoPago() != null ? pagoDTO.getMetodoPago() : "EFECTIVO");
        pago.setEstado("APROBADO");
        pago.setFechaValidacion(LocalDate.now());

        Pago pagoGuardado = pagoRepository.save(pago);

        // Actualizar cuota a PAGADO
        cuota.setEstado(Cuota.EstadoCuota.PAGADO);
        cuotaRepository.save(cuota);

        verificarCreditoCompletado(cuota);

        return pagoGuardado;
    }

    @Override
    @Transactional
    public Pago registrarPagoConComprobante(Long cuotaId, MultipartFile comprobante) {
        Cuota cuota = cuotaRepository.findById(cuotaId)
                .orElseThrow(() -> new RuntimeException("Cuota con ID " + cuotaId + " no encontrada"));

        if (cuota.getEstado() == Cuota.EstadoCuota.PAGADO) {
            throw new RuntimeException("La cuota ya se encuentra pagada");
        }
        if (cuota.getEstado() == Cuota.EstadoCuota.EN_REVISION) {
            throw new RuntimeException("Esta cuota ya tiene un comprobante en revisión");
        }

        String secureUrl = null;
        String publicId = null;

        if (comprobante != null && !comprobante.isEmpty()) {
            try {
                Map<String, Object> uploadResult = cloudinaryService.subirComprobante(comprobante);
                secureUrl = (String) uploadResult.get("secure_url");
                publicId = (String) uploadResult.get("public_id");
            } catch (IOException e) {
                log.error("Error al subir comprobante a Cloudinary: {}", e.getMessage());
                throw new RuntimeException("No se pudo subir la imagen del comprobante: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Debe adjuntar la imagen del comprobante");
        }

        // Crear registro de pago en estado PENDIENTE
        Pago pago = new Pago();
        pago.setCuota(cuota);
        pago.setMonto(cuota.getMonto());
        pago.setFechaPago(LocalDate.now());
        pago.setMetodoPago("YAPE");
        pago.setEstado("PENDIENTE");
        pago.setComprobanteUrl(secureUrl);
        pago.setPublicIdCloudinary(publicId);

        Pago pagoGuardado = pagoRepository.save(pago);

        // Cambiar estado de la cuota a EN_REVISION
        cuota.setEstado(Cuota.EstadoCuota.EN_REVISION);
        cuotaRepository.save(cuota);

        // Notificar a Telegram si está activo
        try {
            User cliente = cuota.getCredito().getVenta().getCliente();
            String nombreCliente = cliente.getName() + " " + cliente.getLastname();
            String telefono = cliente.getPhone();
            telegramNotificationService.notificarNuevoPagoYape(
                    pagoGuardado.getId(),
                    nombreCliente,
                    telefono,
                    cuota.getNumeroCuota(),
                    cuota.getMonto(),
                    secureUrl
            );
        } catch (Exception ex) {
            log.warn("No se pudo enviar la alerta de Telegram: {}", ex.getMessage());
        }

        return pagoGuardado;
    }

    @Override
    @Transactional
    public Pago validarPago(Long pagoId, String nuevoEstado, String motivoRechazo) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new RuntimeException("Pago con ID " + pagoId + " no encontrado"));

        Cuota cuota = pago.getCuota();

        if ("APROBADO".equalsIgnoreCase(nuevoEstado)) {
            pago.setEstado("APROBADO");
            pago.setFechaValidacion(LocalDate.now());
            pago.setMotivoRechazo(null);

            boolean esAbonoLibre = (pago.getTipoAbono() != null && !pago.getTipoAbono().isBlank());
            boolean esFiado = (cuota.getCredito() != null &&
                    cuota.getCredito().getVenta() != null &&
                    cuota.getCredito().getVenta().getTipoVenta() == Venta.TipoVenta.FIADO);

            // Si es un abono libre registrado a una cuenta, o una venta fiada, amortizamos FIFO dentro de esa cuenta.
            // Si fue un pago directo a una cuota individual específica con monto exacto, se liquida esa cuota directamente.
            if (esAbonoLibre || (esFiado && pago.getMonto().compareTo(cuota.getMonto()) != 0)) {
                amortizarPagoFIFO(pago);
            } else {
                cuota.setEstado(Cuota.EstadoCuota.PAGADO);
                cuotaRepository.save(cuota);
                verificarCreditoCompletado(cuota);
            }
        } else if ("RECHAZADO".equalsIgnoreCase(nuevoEstado)) {
            pago.setEstado("RECHAZADO");
            pago.setFechaValidacion(LocalDate.now());
            pago.setMotivoRechazo(motivoRechazo != null && !motivoRechazo.isBlank() ? motivoRechazo : "Comprobante rechazado por el administrador");

            // La cuota vuelve a estar PENDIENTE para que el cliente pueda volver a intentar
            cuota.setEstado(Cuota.EstadoCuota.PENDIENTE);
            cuotaRepository.save(cuota);
        } else {
            throw new IllegalArgumentException("Estado inválido. Debe ser APROBADO o RECHAZADO");
        }

        return pagoRepository.save(pago);
    }

    private void amortizarPagoFIFO(Pago pago) {
        Cuota cuotaOrigen = pago.getCuota();
        Long clienteId = Long.valueOf(cuotaOrigen.getCredito().getVenta().getCliente().getId());
        BigDecimal saldoAbono = pago.getMonto();

        // Determinar qué tipo de cuotas amortizar:
        // Si tipoAbono es 'FIADO', amortizar únicamente ventas FIADO.
        // Si tipoAbono es 'CREDITO', amortizar únicamente ventas CREDITO.
        // Si no se especificó o es individual, inferir por el tipo de venta de la cuota pagada.
        String tipo = pago.getTipoAbono();
        if (tipo == null || tipo.isBlank() || "TODO".equalsIgnoreCase(tipo)) {
            if (cuotaOrigen.getCredito() != null && cuotaOrigen.getCredito().getVenta() != null) {
                tipo = cuotaOrigen.getCredito().getVenta().getTipoVenta().name();
            } else {
                tipo = "FIADO";
            }
        }

        final String tipoFiltro = tipo;

        // Obtenemos todas las cuotas activas (PENDIENTE, EN_REVISION, VENCIDO) en orden cronológico FIFO
        // y filtramos ESTRICTAMENTE por el tipo de cuenta seleccionado (FIADO o CREDITO)
        List<Cuota> cuotasActivas = cuotaRepository.findCuotasByClienteIdOrdered(clienteId).stream()
                .filter(c -> c.getEstado() != Cuota.EstadoCuota.PAGADO)
                .filter(c -> {
                    if (c.getCredito() == null || c.getCredito().getVenta() == null) return false;
                    String tipoVenta = c.getCredito().getVenta().getTipoVenta().name();
                    return tipoVenta.equalsIgnoreCase(tipoFiltro);
                })
                .sorted((c1, c2) -> {
                    LocalDate f1 = c1.getCredito().getVenta().getFechaVenta();
                    LocalDate f2 = c2.getCredito().getVenta().getFechaVenta();
                    int cmp = f1.compareTo(f2);
                    return (cmp != 0) ? cmp : c1.getId().compareTo(c2.getId());
                })
                .toList();

        for (Cuota c : cuotasActivas) {
            if (saldoAbono.compareTo(BigDecimal.ZERO) <= 0) {
                // Si la cuota quedó en EN_REVISION y ya no hay saldo para cubrirla, vuelve a PENDIENTE
                if (c.getEstado() == Cuota.EstadoCuota.EN_REVISION) {
                    c.setEstado(Cuota.EstadoCuota.PENDIENTE);
                    cuotaRepository.save(c);
                }
                continue;
            }

            if (saldoAbono.compareTo(c.getMonto()) >= 0) {
                // El abono cubre completamente o supera esta cuota
                saldoAbono = saldoAbono.subtract(c.getMonto());
                c.setEstado(Cuota.EstadoCuota.PAGADO);
                cuotaRepository.save(c);
                verificarCreditoCompletado(c);
            } else {
                // El abono cubre parcialmente esta cuota: se descuenta de su saldo pendiente
                BigDecimal nuevoMonto = c.getMonto().subtract(saldoAbono);
                c.setMonto(nuevoMonto);
                c.setEstado(Cuota.EstadoCuota.PENDIENTE);
                cuotaRepository.save(c);
                saldoAbono = BigDecimal.ZERO;
            }
        }
    }

    private void verificarCreditoCompletado(Cuota cuota) {
        Credito credito = cuota.getCredito();
        List<Cuota> cuotas = cuotaRepository.findByCreditoId(credito.getId());
        boolean todasPagadas = cuotas.stream()
                .allMatch(c -> c.getEstado() == Cuota.EstadoCuota.PAGADO);

        if (todasPagadas) {
            credito.setEstado(Credito.EstadoCredito.PAGADO);
            creditoRepository.save(credito);

            Venta venta = credito.getVenta();
            venta.setEstado(Venta.EstadoVenta.PAGADO);
            ventaRepository.save(venta);
        }
    }

    @Override
    public List<Pago> obtenerPagosPorCuota(Long cuotaId) {
        return pagoRepository.findByCuotaId(cuotaId);
    }

    @Override
    public List<Pago> obtenerPagosPorCliente(Long clienteId) {
        return pagoRepository.findByCuotaCreditoVentaClienteIdOrderByFechaPagoDesc(clienteId);
    }

    @Override
    public List<Pago> listarPagosPendientes() {
        return pagoRepository.findByEstadoOrderByFechaPagoDesc("PENDIENTE");
    }

    @Override
    public List<Pago> listarTodosLosPagos() {
        return pagoRepository.findAllByOrderByFechaPagoDesc();
    }

    @Override
    @Transactional
    public Pago registrarAbonoFiado(Long clienteId, BigDecimal monto, MultipartFile comprobante, String tipoAbono) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del abono debe ser mayor a 0");
        }

        String tipo = (tipoAbono != null && !tipoAbono.isBlank()) ? tipoAbono.toUpperCase() : "FIADO";

        List<Cuota> cuotasPendientes;
        if ("FIADO".equalsIgnoreCase(tipo)) {
            cuotasPendientes = cuotaRepository.findCuotasActivasFiadoByClienteIdFIFO(clienteId);
        } else if ("CREDITO".equalsIgnoreCase(tipo)) {
            cuotasPendientes = cuotaRepository.findCuotasActivasCreditoByClienteIdFIFO(clienteId);
        } else {
            cuotasPendientes = cuotaRepository.findCuotasActivasByClienteIdFIFO(clienteId);
        }

        if (cuotasPendientes.isEmpty()) {
            String cuentaNombre = "FIADO".equalsIgnoreCase(tipo) ? "de fiados en bodega" : "de créditos";
            throw new RuntimeException("El cliente no tiene deudas pendientes activas " + cuentaNombre + " para amortizar");
        }

        String secureUrl = null;
        String publicId = null;
        if (comprobante != null && !comprobante.isEmpty()) {
            try {
                Map<String, Object> uploadResult = cloudinaryService.subirComprobante(comprobante);
                secureUrl = (String) uploadResult.get("secure_url");
                publicId = (String) uploadResult.get("public_id");
            } catch (IOException e) {
                log.error("Error al subir comprobante a Cloudinary: {}", e.getMessage());
                throw new RuntimeException("No se pudo subir la imagen del comprobante: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("Debe adjuntar la imagen del comprobante de Yape");
        }

        // Asignamos el pago a la cuota más antigua pendiente del tipo seleccionado
        Cuota primeraCuota = cuotasPendientes.get(0);

        Pago pago = new Pago();
        pago.setCuota(primeraCuota);
        pago.setMonto(monto);
        pago.setFechaPago(LocalDate.now());
        pago.setMetodoPago("YAPE");
        pago.setEstado("PENDIENTE");
        pago.setTipoAbono(tipo);
        pago.setComprobanteUrl(secureUrl);
        pago.setPublicIdCloudinary(publicId);

        Pago pagoGuardado = pagoRepository.save(pago);

        // Marcamos la primera cuota en revisión
        primeraCuota.setEstado(Cuota.EstadoCuota.EN_REVISION);
        cuotaRepository.save(primeraCuota);

        // Notificar por Telegram
        try {
            User cliente = primeraCuota.getCredito().getVenta().getCliente();
            String nombreCliente = cliente.getName() + " " + cliente.getLastname();
            String telefono = cliente.getPhone();
            telegramNotificationService.notificarNuevoPagoYape(
                    pagoGuardado.getId(),
                    nombreCliente,
                    telefono,
                    primeraCuota.getNumeroCuota(),
                    monto,
                    secureUrl
            );
        } catch (Exception ex) {
            log.warn("No se pudo enviar la alerta de Telegram: {}", ex.getMessage());
        }

        return pagoGuardado;
    }
}
