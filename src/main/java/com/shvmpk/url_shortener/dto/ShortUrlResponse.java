package com.shvmpk.url_shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortUrlResponse {
    private String shortUrl;
    private String aliasUrl;
    private String qrCodeUrl;
    private String expiresAt;
    private Integer maxClicksAllowed;
    private Boolean passwordProtected;
}
