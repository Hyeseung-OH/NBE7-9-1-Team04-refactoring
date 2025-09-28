package com.backend.ut.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import java.util.Map;

public class Ut {
    public static class jwt{
        public String toString(String secret, long expireSeconds, Map<String,Object> body){
            ClaimsBuilder claimsBuilder = Jwts.claims();

            for(Map.Entry<String, Object> entry : body.entrySet()){
                claimsBuilder.add(entry.getKey(), entry.getValue());
            }

            Claims claims = claimsBuilder.build();

            Date issuedAt = new Date();
            Date expiresAt = new Date(issuedAt.getTime() + 1000L *  expireSeconds);

            Key secretKey = Keys.hmacShaKeyFor(secret.getBytes());

            String jwt = Jwts.builder()
                    .claims(claims)
                    .issuedAt(issuedAt)
                    .expiration(expiresAt)
                    .signWith(secretKey)
                    .compact();

            return jwt;
        }
    }
}
