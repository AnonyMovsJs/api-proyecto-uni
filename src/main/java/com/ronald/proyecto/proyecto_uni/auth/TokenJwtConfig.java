package com.ronald.proyecto.proyecto_uni.auth;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;

public class TokenJwtConfig {

    // Con esta llave secreta firmamos el token
    public static final SecretKey SECRET_KEY = Jwts.SIG.HS256.key().build();
}
