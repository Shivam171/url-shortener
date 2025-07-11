package com.shvmpk.url_shortener.util;

import java.net.NetworkInterface;
import java.util.Enumeration;

public class MachineId {
    private static final int MAX_MACHINE_ID = 1023;

    public static long getMachineId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;

                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length >= 2) {
                    long raw = ((mac[mac.length - 2] & 0xFFL) << 8) | (mac[mac.length - 1] & 0xFFL);
                    return raw % (MAX_MACHINE_ID + 1);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get machine ID: " + e.getMessage());
        }
        return 0L; // fallback
    }
}
