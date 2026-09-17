package com.ronald.proyecto.proyecto_uni.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final String botToken;
    private final String defaultChatId;
    private final RestTemplate restTemplate;

    private boolean notificationsEnabled = true;

    public TelegramNotificationService(
            @Value("${telegram.bot.token:}") String botToken,
            @Value("${telegram.chat.id:}") String defaultChatId,
            @Value("${telegram.notifications.enabled:true}") boolean notificationsEnabled) {
        this.botToken = botToken;
        this.defaultChatId = defaultChatId;
        this.notificationsEnabled = notificationsEnabled;
        this.restTemplate = new RestTemplate();
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean enabled) {
        this.notificationsEnabled = enabled;
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && defaultChatId != null && !defaultChatId.isBlank();
    }

    public boolean notificarCodigo2FA(String email, String telefono, String code) {
        if (!notificationsEnabled) {
            log.info("Notificaciones de Telegram desactivadas. No se envía código 2FA.");
            return false;
        }

        if (!isConfigured()) {
            log.warn("Telegram Bot no configurado (falta TELEGRAM_BOT_TOKEN o TELEGRAM_CHAT_ID). Se omite el envío.");
            return false;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            String texto = String.format(
                    "🔐 *CÓDIGO DE VERIFICACIÓN 2FA*\n\n" +
                    "👤 *Usuario:* %s\n" +
                    "📱 *Teléfono:* %s\n" +
                    "🔑 *Código:* `%s`\n\n" +
                    "⏳ *Válido por 2 minutos.*\n" +
                    "_Comercial Reyes - Sistema de Seguridad_",
                    email != null ? email : "Usuario",
                    telefono != null ? telefono : "S/N",
                    code
            );

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", defaultChatId);
            payload.put("text", texto);
            payload.put("parse_mode", "Markdown");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, request, String.class);

            log.info("Código 2FA enviado exitosamente a Telegram para {}", email);
            return true;
        } catch (Exception e) {
            log.error("Error al enviar código 2FA a Telegram para {}: {}", email, e.getMessage());
            return false;
        }
    }

    public void notificarNuevoPagoYape(Long pagoId, String nombreCliente, String telefono, Integer numeroCuota, BigDecimal monto, String comprobanteUrl) {
        if (!notificationsEnabled) {
            log.info("Notificaciones de Telegram desactivadas por el usuario. No se envía alerta para el pago ID {}", pagoId);
            return;
        }

        if (!isConfigured()) {
            log.warn("Telegram Bot no configurado (falta TELEGRAM_BOT_TOKEN o TELEGRAM_CHAT_ID). Se omite el envío.");
            return;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            String texto = String.format(
                    "🔔 *NUEVO PAGO POR VALIDAR (YAPE)*\n\n" +
                    "👤 *Cliente:* %s\n" +
                    "📱 *Teléfono:* %s\n" +
                    "💳 *Cuota N°:* %d\n" +
                    "💰 *Monto:* S/ %.2f\n" +
                    "🆔 *Pago ID:* #%d\n\n" +
                    "📸 [Ver Comprobante en Cloudinary](%s)",
                    nombreCliente != null ? nombreCliente : "Desconocido",
                    telefono != null ? telefono : "S/N",
                    numeroCuota != null ? numeroCuota : 0,
                    monto != null ? monto : BigDecimal.ZERO,
                    pagoId,
                    comprobanteUrl != null ? comprobanteUrl : "#"
            );

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", defaultChatId);
            payload.put("text", texto);
            payload.put("parse_mode", "Markdown");

            // Botones interactivos Inline
            Map<String, String> btnAprobar = Map.of("text", "✅ Aceptar Pago", "callback_data", "PAGO_APROBAR_" + pagoId);
            Map<String, String> btnRechazar = Map.of("text", "❌ Rechazar Pago", "callback_data", "PAGO_RECHAZAR_" + pagoId);
            List<List<Map<String, String>>> inlineKeyboard = List.of(List.of(btnAprobar, btnRechazar));

            payload.put("reply_markup", Map.of("inline_keyboard", inlineKeyboard));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(url, request, String.class);

            log.info("Notificación de pago #{} enviada exitosamente a Telegram", pagoId);
        } catch (Exception e) {
            log.error("Error al enviar notificación a Telegram para pago #{}: {}", pagoId, e.getMessage());
        }
    }
}
