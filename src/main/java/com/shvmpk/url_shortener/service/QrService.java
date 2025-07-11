package com.shvmpk.url_shortener.service;

import com.shvmpk.url_shortener.config.AppProperties;
import com.shvmpk.url_shortener.util.QRCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QrService {
    private final AppProperties appProperties;

    public byte[] generateQrCode(String urlOrAlias, int size, String color, String overlayText, String logoUrl, String format) throws Exception {
        String fullUrl = appProperties.baseUrl() + "/" + urlOrAlias;

        return QRCodeGenerator.generate(fullUrl, size, color, overlayText, logoUrl, format);
    }
}
