package com.ronald.proyecto.proyecto_uni.auth;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;

public class TokenJwtConfig {

    // Con esta llave secreta fija firmamos el token para que persista entre reinicios del servidor
    public static final SecretKey SECRET_KEY = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
        "ClaveSecretaSuperSeguraParaProyectoUniConMasDe256Bits2025!".getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );

    // Duración del JWT: 24 horas (1 día completo en milisegundos)
    public static final long EXPIRATION_TIME = 86_400_000L;
}
