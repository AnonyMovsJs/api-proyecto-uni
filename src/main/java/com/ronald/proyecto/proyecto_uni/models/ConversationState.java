package com.ronald.proyecto.proyecto_uni.models;

import java.util.HashMap;
import java.util.Map;

public class ConversationState {
    private String currentState = "IDLE"; // Estado actual (IDLE, CREATING_USER, ASKING_CREDIT, etc.)
    private Map<String, Object> contextData = new HashMap<>(); // Datos del contexto
    private int step = 0; // Paso actual en un flujo multistep

    public String getCurrentState() {
        return currentState;
    }

    public void setCurrentState(String currentState) {
        this.step = 0; // Reiniciar el paso al cambiar de estado
        this.currentState = currentState;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public void incrementStep() {
        this.step++;
    }

    public Map<String, Object> getContextData() {
        return contextData;
    }

    public void addToContext(String key, Object value) {
        this.contextData.put(key, value);
    }

    public Object getFromContext(String key) {
        return this.contextData.get(key);
    }

    public void clearContext() {
        this.contextData.clear();
        this.currentState = "IDLE";
        this.step = 0;
    }

    @Override
    public String toString() {
        return "ConversationState{" +
                "currentState='" + currentState + '\'' +
                ", step=" + step +
                ", contextData=" + contextData +
                '}';
    }
}