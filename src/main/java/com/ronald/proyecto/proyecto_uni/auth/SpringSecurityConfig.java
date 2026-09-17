package com.ronald.proyecto.proyecto_uni.auth;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.ronald.proyecto.proyecto_uni.auth.filter.JwtAuthenticationFilter;
import com.ronald.proyecto.proyecto_uni.auth.filter.JwtValidationFilter;

@Configuration 
public class SpringSecurityConfig {

    @Autowired
    private AuthenticationConfiguration authenticationConfiguration;

    @Bean
    AuthenticationManager authenticationManager() throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }


    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); 
    }


    @Bean 
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http

                .authorizeHttpRequests(authz -> authz

                        //USERS-----------------------------------------------------------
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/page/{page}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users/{id}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users/profile").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/{id}").hasRole("ADMIN")
                        //VENTAS-----------------------------------------------------------
                        .requestMatchers(HttpMethod.POST, "/api/ventas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/ventas/cliente/{clienteId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/ventas/{ventaId}").hasAnyRole("USER","ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/ventas/{ventaId}/detalles").hasAnyRole("USER", "ADMIN")
                        //CRÉDITOS----------------------------------------------------------
                        .requestMatchers(HttpMethod.GET, "/api/creditos/cliente/{clienteId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/creditos/venta/{ventaId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/creditos/{creditoId}/cuotas").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/creditos/cuota/**").hasRole("ADMIN")
                        .requestMatchers("/api/cuotas/**").hasAnyRole("USER", "ADMIN")
                        //PAGOS-------------------------------------------------------------
                        .requestMatchers(HttpMethod.POST, "/api/pagos", "/api/pagos/yape").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/pagos/{pagoId}/validar").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/pagos/pendientes", "/api/pagos").hasRole("ADMIN")
                        .requestMatchers("/api/pagos/config/telegram/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/pagos/cuota/{cuotaId}").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/pagos/cliente/{clienteId}").hasAnyRole("USER", "ADMIN")
                        // CHATBOT--------------------------------------------------------
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/message").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/ws-chatbot/**").permitAll() // Para WebSockets

                        // NOTIFICACIONES-------------------------------------------------
                        .requestMatchers(HttpMethod.POST, "/api/notificaciones").hasRole("ADMIN")
                        .requestMatchers("/api/notificaciones/**").hasAnyRole("USER", "ADMIN")

                        //SMS AUTH------------------------------------------------------
                        .requestMatchers(HttpMethod.POST, "/api/auth/verify-sms").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/send-whatsapp").permitAll()
                        .anyRequest().authenticated())
                .cors(cors -> cors.configurationSource(configurationSource()))
                .addFilter(new JwtAuthenticationFilter(authenticationManager()))
                .addFilter(new JwtValidationFilter(authenticationManager()))
                .csrf(config -> config.disable())
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    CorsConfigurationSource configurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(Arrays.asList("*"));

        config.setAllowedOrigins(Arrays.asList("http://localhost:4200"));

        config.setAllowedMethods(Arrays.asList("POST", "GET", "PUT", "DELETE", "PATCH", "OPTIONS"));

        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFiler() {
        FilterRegistrationBean<CorsFilter> corsBean = new FilterRegistrationBean<CorsFilter>(
                new CorsFilter(this.configurationSource()));

        corsBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return corsBean;
    }
}
