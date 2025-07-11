package com.shvmpk.url_shortener.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrRequest {
    private int size = 250;

    private String color;

    private String overlayText;

    private String logoUrl;

    @Pattern(regexp = "^(png|jpg|jpeg|svg|base64)$", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Only PNG, JPG, JPEG, SVG, or BASE64 formats are allowed.")
    private String format = "png";
}

