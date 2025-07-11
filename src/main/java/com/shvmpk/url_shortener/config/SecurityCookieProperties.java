package com.shvmpk.url_shortener.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "security.cookie-encryption")
public class SecurityCookieProperties {
    private List<KeyPair> keys;

    @Data
    public static class KeyPair {
        private String password;
        private String salt;
    }
}