package com.ronald.proyecto.proyecto_uni.service;

import com.ronald.proyecto.proyecto_uni.models.ChatRequest;
import com.ronald.proyecto.proyecto_uni.models.ChatResponse;

public interface ChatbotService {

    ChatResponse processChatMessage(ChatRequest request);
    
}
