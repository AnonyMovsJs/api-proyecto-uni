package com.ronald.proyecto.proyecto_uni.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.models.ChatRequest;
import com.ronald.proyecto.proyecto_uni.models.ChatResponse;
import com.ronald.proyecto.proyecto_uni.service.ChatbotService;

@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = { "http://localhost:4200", "http://127.0.0.1:4200" })
public class ChatbotRestController {

    @Autowired
    private ChatbotService chatbotService;

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> processMessage(@RequestBody ChatRequest request) {
        try {
            // Obtener información de autenticación
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // No intentar convertir directamente a UserDetails, sino verificar el tipo
            if (authentication != null && authentication.isAuthenticated()) {
                // Establecer el rol según las autoridades
                String role = "USER";
                if (authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                    role = "ADMIN";
                }

                // Si no se ha proporcionado un rol, configurarlo
                if (request.getUserRole() == null || request.getUserRole().isEmpty()) {
                    request.setUserRole(role);
                }

                // Si no se ha proporcionado un ID de usuario, obtenerlo si es posible
                if (request.getUserId() == null || request.getUserId().isEmpty() || request.getUserId().equals("0")) {
                    // Intentar obtener el ID de usuario del principal
                    Object principal = authentication.getPrincipal();
                    if (principal instanceof User) {
                        User user = (User) principal;
                        request.setUserId(user.getId().toString());
                    } else if (authentication.getName() != null) {
                        // Si no es un User, usar el nombre para buscar el usuario
                        request.setUserId(authentication.getName());
                    }
                }
            }

            // Procesar mensaje
            ChatResponse response = chatbotService.processChatMessage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Lo siento, ha ocurrido un error al procesar tu mensaje: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
}