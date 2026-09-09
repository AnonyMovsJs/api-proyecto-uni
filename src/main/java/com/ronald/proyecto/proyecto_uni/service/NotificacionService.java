package com.ronald.proyecto.proyecto_uni.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ronald.proyecto.proyecto_uni.entity.Notificacion;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.models.NotificacionDTO;
import com.ronald.proyecto.proyecto_uni.repository.NotificacionRepository;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;

@Service
public class NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Notificacion crearNotificacion(NotificacionDTO dto) {
        User cliente = userRepository.findById(dto.getClienteId())
            .orElseThrow(() -> new RuntimeException("Cliente no encontrado con id: " + dto.getClienteId()));

        Notificacion notif = new Notificacion();
        notif.setCliente(cliente);
        notif.setTitulo(dto.getTitulo());
        notif.setMensaje(dto.getMensaje());
        notif.setMontoDeuda(dto.getMontoDeuda());
        notif.setDiasRetraso(dto.getDiasRetraso());
        notif.setFechaVencimiento(dto.getFechaVencimiento() != null ? dto.getFechaVencimiento() : LocalDateTime.now());
        notif.setFechaEnvio(LocalDateTime.now());
        notif.setLeida(false);
        notif.setTipo(dto.getTipo() != null ? dto.getTipo() : "RECORDATORIO");

        return notificacionRepository.save(notif);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> listarPorCliente(Integer clienteId) {
        return notificacionRepository.findByClienteIdOrderByFechaEnvioDesc(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Notificacion> listarNoLeidasPorCliente(Integer clienteId) {
        return notificacionRepository.findByClienteIdAndLeidaFalseOrderByFechaEnvioDesc(clienteId);
    }

    @Transactional(readOnly = true)
    public long contarNoLeidas(Integer clienteId) {
        return notificacionRepository.countByClienteIdAndLeidaFalse(clienteId);
    }

    @Transactional
    public Notificacion marcarComoLeida(Long notificacionId) {
        Notificacion notif = notificacionRepository.findById(notificacionId)
            .orElseThrow(() -> new RuntimeException("Notificación no encontrada"));
        notif.setLeida(true);
        return notificacionRepository.save(notif);
    }

    @Transactional
    public void marcarTodasComoLeidas(Integer clienteId) {
        List<Notificacion> pendientes = notificacionRepository.findByClienteIdAndLeidaFalseOrderByFechaEnvioDesc(clienteId);
        for (Notificacion n : pendientes) {
            n.setLeida(true);
        }
        notificacionRepository.saveAll(pendientes);
    }
}
