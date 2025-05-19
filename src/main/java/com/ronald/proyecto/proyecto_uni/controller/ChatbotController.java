package com.ronald.proyecto.proyecto_uni.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.ronald.proyecto.proyecto_uni.models.ChatRequest;
import com.ronald.proyecto.proyecto_uni.models.ChatResponse;
import com.ronald.proyecto.proyecto_uni.service.ChatbotService;

@Controller
@CrossOrigin(origins = "http://localhost:4200")
public class ChatbotController {

    @Autowired
    private ChatbotService chatbotService;

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatResponse sendMessage(@Payload ChatRequest chatRequest) {
        return chatbotService.processChatMessage(chatRequest);
    }

    @SuppressWarnings("null")
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatResponse addUser(@Payload ChatRequest chatRequest,
            SimpMessageHeaderAccessor headerAccessor) {
        // Agregar usuario al WebSocket
        headerAccessor.getSessionAttributes().put("username", chatRequest.getUserId());

        ChatResponse response = new ChatResponse();
        response.setSuccess(true);
        response.setMessage("Bienvenido al asistente virtual de tu sistema de ventas. " +
                "¿En qué puedo ayudarte hoy?");
        return response;
    }
}
