package com.shvmpk.url_shortener.util;


import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class QRCodeGenerator {

    public static byte[] generate(String text, int size, String hexColor, String overlayText, String logoUrl, String format) throws Exception {
        if ("svg".equalsIgnoreCase(format)) {
            return generateSvg(text, size);
        }

        BufferedImage qrImage = generateBufferedImage(text, size, hexColor, overlayText, logoUrl);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, format.equalsIgnoreCase("jpg") ? "jpeg" : format, baos);
        return baos.toByteArray();
    }

    private static BufferedImage generateBufferedImage(String text, int size, String hexColor, String overlayText, String logoUrl) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);

        Color color = (hexColor != null && !hexColor.isBlank()) ? Color.decode("#" + hexColor) : Color.BLACK;
        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix, new MatrixToImageConfig(color.getRGB(), Color.WHITE.getRGB()));

        Graphics2D g = qrImage.createGraphics();

        if (overlayText != null && !overlayText.isBlank()) {
            g.setColor(color);
            g.setFont(new Font("Arial", Font.BOLD, size / 12));
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(overlayText);
            g.drawString(overlayText, (size - textWidth) / 2, size - 10);
        }

        if (logoUrl != null && !logoUrl.isBlank()) {
            try (InputStream in = new URL(logoUrl).openStream()) {
                BufferedImage logo = ImageIO.read(in);
                int logoSize = size / 5;
                int x = (size - logoSize) / 2;
                int y = (size - logoSize) / 2;
                g.drawImage(logo, x, y, logoSize, logoSize, null);
            } catch (Exception e) {
                System.err.println("Logo failed: " + e.getMessage());
            }
        }

        g.dispose();
        return qrImage;
    }

    private static byte[] generateSvg(String text, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(size).append("\" height=\"").append(size)
                .append("\" shape-rendering=\"crispEdges\">");

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (matrix.get(x, y)) {
                    svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                            .append("\" width=\"1\" height=\"1\" fill=\"black\"/>");
                }
            }
        }
        svg.append("</svg>");
        return svg.toString().getBytes(StandardCharsets.UTF_8);
    }
}