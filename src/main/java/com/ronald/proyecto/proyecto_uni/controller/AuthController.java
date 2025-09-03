package com.ronald.proyecto.proyecto_uni.controller;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.ronald.proyecto.proyecto_uni.service.impl.SmsService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.validation.Valid;

import static com.ronald.proyecto.proyecto_uni.auth.TokenJwtConfig.*;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SmsService smsService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/verify-sms")
    public ResponseEntity<?> verifySms(@Valid @RequestBody SmsVerificationRequest request) {
        try {
            System.out.println("=== INICIANDO VERIFICACIÓN SMS ===");
            System.out.println("Email recibido: " + request.getEmail());
            System.out.println("Código recibido: " + request.getCode());
            System.out.println("TempToken recibido: " + request.getTempToken());
            System.out.println("IsAdmin recibido: " + request.isAdmin());

            // Verificar código SMS
            System.out.println("Verificando código SMS...");
            boolean isValid = smsService.verifySmsCode(request.getEmail(), request.getCode());

            if (!isValid) {
                System.out.println("❌ Código SMS inválido");
                Map<String, String> response = new HashMap<>();
                response.put("message", "Código SMS inválido o expirado");
                response.put("error", "INVALID_SMS_CODE");
                return ResponseEntity.badRequest().body(response);
            }

            System.out.println("✅ Código SMS válido");

            // Obtener usuario
            System.out.println("Buscando usuario...");
            Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
            if (!userOpt.isPresent()) {
                System.out.println("❌ Usuario no encontrado");
                Map<String, String> response = new HashMap<>();
                response.put("message", "Usuario no encontrado");
                response.put("error", "USER_NOT_FOUND");
                return ResponseEntity.badRequest().body(response);
            }

            User user = userOpt.get();
            System.out.println("Usuario encontrado: " + user.getEmail());
            System.out.println("Roles del usuario: " + user.getRoles().size());

            // Verificar SECRET_KEY
            System.out.println("SECRET_KEY disponible: " + (SECRET_KEY != null));
            if (SECRET_KEY == null) {
                System.out.println("❌ ERROR: SECRET_KEY es null");
                Map<String, String> response = new HashMap<>();
                response.put("message", "Error interno: clave JWT no configurada");
                response.put("error", "JWT_CONFIG_ERROR");
                return ResponseEntity.internalServerError().body(response);
            }

            // Generar authorities
            System.out.println("Generando authorities...");
            Collection<? extends GrantedAuthority> authorities = getUserAuthorities(user);
            System.out.println("Authorities generadas: " + authorities.size());

            String authoritiesJson = new ObjectMapper().writeValueAsString(authorities);
            System.out.println("Authorities JSON: " + authoritiesJson);

            // Generar JWT final
            System.out.println("Generando JWT...");
            Claims claims = Jwts
                    .claims()
                    .add("authorities", authoritiesJson)
                    .add("email", user.getEmail())
                    .add("isAdmin", request.isAdmin())
                    .build();

            System.out.println("Claims creados correctamente");

            String jwt = Jwts.builder()
                    .subject(user.getEmail())
                    .claims(claims)
                    .signWith(SECRET_KEY)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hora
                    .compact();

            System.out.println("JWT generado correctamente");
            System.out.println("JWT length: " + (jwt != null ? jwt.length() : "null"));

            // Respuesta exitosa
            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("email", user.getEmail());
            response.put("isAdmin", request.isAdmin());
            response.put("message", String.format("Bienvenido %s, autenticación completada", user.getName()));

            System.out.println("✅ Respuesta generada exitosamente");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("❌ ERROR EN VERIFICACIÓN SMS:");
            System.out.println("Tipo de error: " + e.getClass().getSimpleName());
            System.out.println("Mensaje: " + e.getMessage());
            e.printStackTrace();

            Map<String, String> response = new HashMap<>();
            response.put("message", "Error interno del servidor");
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private Collection<? extends GrantedAuthority> getUserAuthorities(User user) {
        return user.getRoles().stream()
                .map(role -> {
                    System.out.println("Procesando rol: " + role.getName());
                    return (GrantedAuthority) () -> role.getName();
                })
                .toList();
    }

    // Clase interna para el request de verificación SMS
    public static class SmsVerificationRequest {
        private String email;
        private String code;
        private String tempToken;
        private boolean admin;

        // Getters y setters
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getTempToken() {
            return tempToken;
        }

        public void setTempToken(String tempToken) {
            this.tempToken = tempToken;
        }

        public boolean isAdmin() {
            return admin;
        }

        public void setAdmin(boolean admin) {
            this.admin = admin;
        }
    }
}