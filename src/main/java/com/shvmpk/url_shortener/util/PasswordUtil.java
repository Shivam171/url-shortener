package com.shvmpk.url_shortener.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.Random;

public class PasswordUtil {
    private static final int COST = 12;

    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) return null;
        return BCrypt.withDefaults().hashToString(COST, plainPassword.toCharArray());
    }

    public static String generateRandomPassword(int length) {
        if (length <= 0) {
            return "";
        }
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+{}[]|:;<>,.?/~`-=";
        Random random = new Random();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            password.append(characters.charAt(index));
        }
        return password.toString();
    }

    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) return false;
        return BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword).verified;
    }
}

