package com.ronald.proyecto.proyecto_uni.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ronald.proyecto.proyecto_uni.entity.User;
import com.ronald.proyecto.proyecto_uni.repository.UserRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

@Service
public class SmsService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String twilioPhoneNumber;

    @Value("${twilio.whatsapp.number}")
    private String twilioWhatsAppNumber;

    @Value("${app.sms.enabled:true}")
    private boolean smsEnabled;

    private UserRepository userRepository;

    public SmsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean sendSmsCode(String email) {
        return sendCode(email, "SMS");
    }

    public boolean sendWhatsAppCode(String email) {
        return sendCode(email, "WHATSAPP");
    }

    private boolean sendCode(String email, String method) {
        try {
            System.out.println("=== ENVIANDO CÓDIGO VÍA " + method + " ===");
            System.out.println("Email: " + email);

            // Buscar usuario por email
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (!userOpt.isPresent()) {
                System.out.println("ERROR: Usuario no encontrado para email: " + email);
                return false;
            }

            User user = userOpt.get();
            System.out.println("Usuario encontrado: " + user.getEmail());

            // Reutilizar código existente si aún es válido (no generar uno nuevo)
            // SIEMPRE usar el mismo código si existe y no ha expirado hace más de 10
            // minutos
            String code;
            if (user.getSmsCode() != null && user.getSmsCodeExpiry() != null &&
                    user.getSmsCodeExpiry().isAfter(LocalDateTime.now().minusMinutes(10))) {
                code = user.getSmsCode();
                // Extender tiempo cada vez que se envía
                user.setSmsCodeExpiry(LocalDateTime.now().plusMinutes(2));
                userRepository.save(user);
                System.out.println("Reutilizando y extendiendo código: " + code);
            } else {
                // Solo generar nuevo si realmente no existe o es muy viejo
                code = generateSmsCode();
                user.setSmsCode(code);
                user.setSmsCodeExpiry(LocalDateTime.now().plusMinutes(2));
                user.setSmsVerified(false);
                userRepository.save(user);
                System.out.println("Nuevo código generado: " + code);
            }

            String formattedPhone = "+51" + user.getPhone();
            System.out.println("Número formateado: " + formattedPhone);

            // Verificar si está en modo desarrollo
            if (!smsEnabled) {
                System.out.println("🔧 MODO DESARROLLO - " + method + " DESACTIVADO");
                System.out.println("📱 Código para testing: " + code);
                System.out.println("📧 Usuario: " + email);
                return true;
            }

            // Inicializar Twilio
            Twilio.init(accountSid, authToken);

            Message message;
            if (method.equals("WHATSAPP")) {
                // Envío por WhatsApp
                message = Message.creator(
                    new PhoneNumber("whatsapp:" + formattedPhone),
                    new PhoneNumber("whatsapp:" + twilioWhatsAppNumber),
                    "🔐 Tu código de verificación es: *" + code + "*\n\nVálido por 2 minutos.\n\n_Mensaje automático - No responder_"
                ).create();
                System.out.println("✅ WhatsApp enviado exitosamente!");
            } else {
                // Envío por SMS tradicional
                message = Message.creator(
                    new PhoneNumber(formattedPhone),
                    new PhoneNumber(twilioPhoneNumber),
                    "Tu código de verificación es: " + code + ". Válido por 2 minutos."
                ).create();
                System.out.println("✅ SMS enviado exitosamente!");
            }

            System.out.println("ID del mensaje: " + message.getSid());
            System.out.println("Estado: " + message.getStatus());
            return true;

        } catch (Exception e) {
            System.out.println("❌ ERROR AL ENVIAR " + method + ":");
            System.out.println("Mensaje: " + e.getMessage());
            e.printStackTrace();
            
            // Simulación en caso de error (para desarrollo)
            System.out.println("🔧 MODO SIMULACIÓN ACTIVADO POR ERROR");
            return true;
        }
    }

    public boolean verifySmsCode(String email, String code) {
        try {
            System.out.println("=== VERIFICANDO CÓDIGO ===");
            System.out.println("Email: " + email);
            System.out.println("Código recibido: " + code);

            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            if (user.getSmsCode() == null || user.getSmsCodeExpiry() == null) {
                System.out.println("No hay código pendiente");
                return false;
            }

            if (LocalDateTime.now().isAfter(user.getSmsCodeExpiry())) {
                System.out.println("Código expirado");
                user.setSmsCode(null);
                user.setSmsCodeExpiry(null);
                userRepository.save(user);
                return false;
            }

            if (user.getSmsCode().equals(code)) {
                System.out.println("✅ Código válido!");
                user.setSmsVerified(true);
                user.setSmsCode(null);
                user.setSmsCodeExpiry(null);
                userRepository.save(user);
                return true;
            }

            System.out.println("❌ Código incorrecto");
            return false;

        } catch (Exception e) {
            System.out.println("Error verificando código: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String generateSmsCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
}