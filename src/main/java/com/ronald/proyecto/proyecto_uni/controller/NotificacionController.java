package com.ronald.proyecto.proyecto_uni.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ronald.proyecto.proyecto_uni.entity.Notificacion;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.models.NotificacionDTO;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.service.NotificacionService;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> crearNotificacion(@RequestBody NotificacionDTO dto) {
        try {
            Notificacion guardada = notificacionService.crearNotificacion(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/cliente/{clienteId}")
    public ResponseEntity<List<Notificacion>> listarPorCliente(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(notificacionService.listarPorCliente(clienteId));
    }

    @GetMapping("/cliente/{clienteId}/no-leidas")
    public ResponseEntity<List<Notificacion>> listarNoLeidas(@PathVariable Integer clienteId) {
        return ResponseEntity.ok(notificacionService.listarNoLeidasPorCliente(clienteId));
    }

    @GetMapping("/cliente/{clienteId}/conteo-no-leidas")
    public ResponseEntity<Map<String, Long>> conteoNoLeidas(@PathVariable Integer clienteId) {
        long total = notificacionService.contarNoLeidas(clienteId);
        return ResponseEntity.ok(Map.of("noLeidas", total));
    }

    @GetMapping("/mis-notificaciones")
    public ResponseEntity<?> misNotificaciones(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User usuario = userRepository.findByEmail(auth.getName())
            .orElse(null);
        if (usuario == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(notificacionService.listarPorCliente(usuario.getId()));
    }

    @GetMapping("/mis-notificaciones/no-leidas")
    public ResponseEntity<?> misNotificacionesNoLeidas(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User usuario = userRepository.findByEmail(auth.getName())
            .orElse(null);
        if (usuario == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(notificacionService.listarNoLeidasPorCliente(usuario.getId()));
    }

    @PutMapping("/{id}/leer")
    public ResponseEntity<?> marcarLeida(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(notificacionService.marcarComoLeida(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/cliente/{clienteId}/leer-todas")
    public ResponseEntity<?> marcarTodasLeidas(@PathVariable Integer clienteId) {
        notificacionService.marcarTodasComoLeidas(clienteId);
        return ResponseEntity.ok(Map.of("mensaje", "Todas marcadas como leídas"));
    }
}
