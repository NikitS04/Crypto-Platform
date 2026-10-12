package com.nikit.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtProvider {

    private static final long EXPIRATION_MS = 86400000;

    private final SecretKey key;

    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Authentication auth){
        String roles = populateAuthorities(auth.getAuthorities());

        return Jwts.builder()
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime()+EXPIRATION_MS))
                .claim("email", auth.getName())
                .claim(JwtConstant.AUTHORITIES_CLAIM, roles)
                .signWith(key)
                .compact();
    }

    // Accepts either a raw token or a "Bearer <token>" header value
    public Claims parseClaims(String token){
        if (token.startsWith("Bearer ")){
            token = token.substring(7);
        }
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public String getEmailFromToken(String token){
        return String.valueOf(parseClaims(token).get("email"));
    }

    private static String populateAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<String> auth = new HashSet<>();
        for(GrantedAuthority ga:authorities){
            auth.add(ga.getAuthority());
        }
        return String.join(",", auth);
    }
}
