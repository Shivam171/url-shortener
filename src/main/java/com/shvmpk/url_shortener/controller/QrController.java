package com.shvmpk.url_shortener.controller;

import com.shvmpk.url_shortener.dto.QrRequest;
import com.shvmpk.url_shortener.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
@CrossOrigin("*")
@Validated
@Tag(name = "QR Management", description = "Endpoints for generating QR codes")
public class QrController {
    private final QrService qrService;

    private static final Set<String> ALLOWED_FORMATS = Set.of("png", "jpg", "jpeg", "svg", "base64");

    // Get: /qr/{urlOrAlias}/{size}/{color}/{overlayText}/{logoUrl}/{format}
    @Operation(summary = "Generate QR code")
    @GetMapping("/{urlOrAlias}")
    public ResponseEntity<?> getQr(
            @PathVariable String urlOrAlias,
            @Valid @ModelAttribute QrRequest qrRequest
    ) throws Exception {
        String format = qrRequest.getFormat().toLowerCase();

        if (!ALLOWED_FORMATS.contains(format)) {
            return ResponseEntity.badRequest().body(
                    "Invalid format. Supported formats: PNG, JPG, JPEG, SVG, BASE64"
            );
        }

        byte[] qrBytes = qrService.generateQrCode(
                urlOrAlias,
                qrRequest.getSize(),
                qrRequest.getColor(),
                qrRequest.getOverlayText(),
                qrRequest.getLogoUrl(),
                format
        );

        if ("base64".equals(format)) {
            String base64 = Base64.getEncoder().encodeToString(qrBytes);
            return ResponseEntity.ok(Map.of("base64", base64));
        }

        MediaType mediaType = switch (format) {
            case "svg" -> MediaType.valueOf("image/svg+xml");
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.IMAGE_PNG;
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"qr_" + urlOrAlias + "." + format + "\"")
                .contentType(mediaType)
                .body(qrBytes);
    }
}
