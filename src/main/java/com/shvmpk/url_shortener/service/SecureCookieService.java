package com.shvmpk.url_shortener.service;

import com.shvmpk.url_shortener.config.SecurityCookieProperties;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecureCookieService {
    private final SecurityCookieProperties config;

    public String encrypt(String value) {
        var key = config.getKeys().get(0); // latest key
        return Encryptors.text(key.getPassword(), key.getSalt()).encrypt(value);
    }

    public boolean matchesDecryption(String encrypted, String expected) {
        for (var key : config.getKeys()) {
            try {
                String decrypted = Encryptors.text(key.getPassword(), key.getSalt()).decrypt(encrypted);
                if (expected.equals(decrypted)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public Cookie createCookie(String name, String value, String path, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, encrypt(value));
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(path);
        cookie.setMaxAge(maxAgeSeconds);
        return cookie;
    }
}