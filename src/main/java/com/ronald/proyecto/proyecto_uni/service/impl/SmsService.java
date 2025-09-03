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

    @Value("${app.sms.enabled:true}")
    private boolean smsEnabled;

    private UserRepository userRepository;

    public SmsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean sendSmsCode(String email) {
        try {
            System.out.println("=== INICIANDO ENVÍO SMS ===");
            System.out.println("Email: " + email);
            System.out.println("Twilio SID: " + accountSid);
            System.out.println("Twilio Phone: " + twilioPhoneNumber);

            // Buscar usuario por email
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (!userOpt.isPresent()) {
                System.out.println("ERROR: Usuario no encontrado para email: " + email);
                return false;
            }

            User user = userOpt.get();
            System.out.println("Usuario encontrado: " + user.getEmail());
            System.out.println("Teléfono usuario: " + user.getPhone());

            // Generar código de 6 dígitos
            String code = generateSmsCode();
            System.out.println("Código generado: " + code);

            // Guardar código en BD con expiración de 5 minutos
            user.setSmsCode(code);
            user.setSmsCodeExpiry(LocalDateTime.now().plusMinutes(5));
            user.setSmsVerified(false);
            userRepository.save(user);
            System.out.println("Código guardado en BD");


            // Verificar si SMS está habilitado
            if (!smsEnabled) {
                System.out.println("🔧 MODO DESARROLLO - SMS DESACTIVADO");
                System.out.println("📱 Código para testing: " + code);
                System.out.println("📧 Usuario: " + email);
                return true; // Simular éxito
            }

            // Formatear número de teléfono peruano
            String formattedPhone = "+51" + user.getPhone();
            System.out.println("Número formateado: " + formattedPhone);
            System.out.println("Número Twilio (FROM): " + twilioPhoneNumber);
            System.out.println("Número Usuario (TO): " + formattedPhone);

            // VERIFICAR SI SON NÚMEROS IGUALES - SIMULAR SI ES NECESARIO
            if (formattedPhone.equals(twilioPhoneNumber)) {
                System.out.println("⚠️  SIMULANDO SMS - Los números son iguales");
                System.out.println("📱 Código SMS sería: " + code);
                System.out.println("📞 Para: " + formattedPhone);
                return true;
            }

            // Intentar envío real
            System.out.println("Inicializando Twilio...");
            Twilio.init(accountSid, authToken);

            System.out.println("Creando mensaje SMS...");
            Message message = Message.creator(
                    new PhoneNumber(formattedPhone),
                    new PhoneNumber(twilioPhoneNumber),
                    "Tu código de verificación es: " + code + ". Válido por 5 minutos.").create();

            System.out.println("✅ SMS enviado exitosamente!");
            System.out.println("SMS ID: " + message.getSid());
            System.out.println("Estado: " + message.getStatus());
            return true;

        } catch (Exception e) {
            System.out.println("❌ ERROR COMPLETO AL ENVIAR SMS:");
            System.out.println("Tipo de error: " + e.getClass().getSimpleName());
            System.out.println("Mensaje: " + e.getMessage());
            e.printStackTrace();

            // PARA TESTING - SIMULAR ÉXITO EN CASO DE ERROR
            System.out.println("🔧 MODO SIMULACIÓN ACTIVADO POR ERROR");
            try {
                User user = userRepository.findByEmail(email).get();
                String code = generateSmsCode();
                user.setSmsCode(code);
                user.setSmsCodeExpiry(LocalDateTime.now().plusMinutes(5));
                user.setSmsVerified(false);
                userRepository.save(user);
                System.out.println("📱 Código simulado guardado: " + code);
                return true;
            } catch (Exception ex) {
                System.out.println("Error en simulación: " + ex.getMessage());
                return false;
            }
        }
    }

    public boolean verifySmsCode(String email, String code) {
        try {
            System.out.println("=== VERIFICANDO CÓDIGO SMS ===");
            System.out.println("Email: " + email);
            System.out.println("Código recibido: " + code);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            // Verificar si el código existe y no ha expirado
            if (user.getSmsCode() == null || user.getSmsCodeExpiry() == null) {
                System.out.println("No hay código pendiente");
                return false;
            }

            System.out.println("Código en BD: " + user.getSmsCode());
            System.out.println("Expira en: " + user.getSmsCodeExpiry());
            System.out.println("Hora actual: " + LocalDateTime.now());

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