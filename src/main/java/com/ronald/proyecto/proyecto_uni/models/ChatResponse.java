package com.ronald.proyecto.proyecto_uni.models;

public class ChatResponse {
    private String message;
    private boolean success;
    private boolean requiresAction;
    private String actionType;
    private Object actionData;
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public boolean isSuccess() {
        return success;
    }
    public void setSuccess(boolean success) {
        this.success = success;
    }
    public boolean isRequiresAction() {
        return requiresAction;
    }
    public void setRequiresAction(boolean requiresAction) {
        this.requiresAction = requiresAction;
    }
    public String getActionType() {
        return actionType;
    }
    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
    public Object getActionData() {
        return actionData;
    }
    public void setActionData(Object actionData) {
        this.actionData = actionData;
    }

    
}
