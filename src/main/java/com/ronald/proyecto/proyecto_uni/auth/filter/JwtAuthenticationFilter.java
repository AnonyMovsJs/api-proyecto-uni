package com.ronald.proyecto.proyecto_uni.auth.filter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ronald.proyecto.proyecto_uni.entity.User;

// Agrega estos imports a tu JwtAuthenticationFilter
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import com.ronald.proyecto.proyecto_uni.service.impl.SmsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {


    private AuthenticationManager authenticationManager;


    public JwtAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        String email = null;
        String password = null;

        try {
            User user = new ObjectMapper().readValue(request.getInputStream(), User.class);
            email = user.getEmail(); 
            password = user.getPassword(); 
        } catch (StreamReadException e) {
            e.printStackTrace(); 
        } catch (DatabindException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(email,
                password);

        return this.authenticationManager.authenticate(authenticationToken); 
    }

    // Reemplaza el método successfulAuthentication completo PASO 5, EL CLAIMS, JWT LO HAREMOS EN EL METODO AuthController.verifySms() → Genera el JWT final:
    // Es una separación correcta de responsabilidades. ❌ NO generar JWT (eso lo hace AuthController)
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authResult) throws IOException, ServletException {

        org.springframework.security.core.userdetails.User user = (org.springframework.security.core.userdetails.User) authResult
                .getPrincipal();
        String email = user.getUsername();
        Collection<? extends GrantedAuthority> roles = authResult.getAuthorities();
        boolean isAdmin = roles.stream().anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));

        // Obtener el SmsService desde el contexto de Spring
        ApplicationContext context = WebApplicationContextUtils.getWebApplicationContext(request.getServletContext());
        if (context == null) {
            Map<String, String> body = new HashMap<>();
            body.put("message", "Error interno: contexto de aplicación no disponible");
            body.put("error", "CONTEXT_NULL");
            response.getWriter().write(new ObjectMapper().writeValueAsString(body));
            response.setContentType("application/json");
            response.setStatus(500);
            return;
        }
        SmsService smsService = context.getBean(SmsService.class);

        // Enviar código SMS
        boolean smsEnviado = smsService.sendSmsCode(email);

        if (!smsEnviado) {
            // Error al enviar SMS
            Map<String, String> body = new HashMap<>();
            body.put("message", "Error al enviar código SMS");
            body.put("error", "SMS_ERROR");
            response.getWriter().write(new ObjectMapper().writeValueAsString(body));
            response.setContentType("application/json");
            response.setStatus(500);
            return;
        }

        // Generar token temporal (no es el JWT final)
        String tempToken = java.util.UUID.randomUUID().toString();

        // Respuesta indicando que debe verificar SMS
        Map<String, Object> body = new HashMap<>();
        body.put("requiresSms", true);
        body.put("tempToken", tempToken);
        body.put("email", email);
        body.put("isAdmin", isAdmin);
        body.put("message", "Se ha enviado un código SMS a tu teléfono");

        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
        response.setContentType("application/json");
        response.setStatus(200);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failed) throws IOException, ServletException {

        Map<String, String> body = new HashMap<>();

        body.put("message", "Error en la autenticación con email o password incorrecto!");
        body.put("error", failed.getMessage());

        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
        response.setContentType("application/json");
        response.setStatus(401);
    }

}
