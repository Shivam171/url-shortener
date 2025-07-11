package com.shvmpk.url_shortener.util;

public class SnowflakeIdGenerator {
    private final long machineId;
    private static final long EPOCH = 1609459200000L; // Jan 1, 2021

    private static final long MACHINE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long machineId) {
        if (machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("Machine ID out of bounds: " + machineId);
        }
        this.machineId = machineId;
    }

    public synchronized long nextId() {
        long timestamp = currentTime();

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                timestamp = waitNextMillis(timestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long currentMillis) {
        while (currentMillis == lastTimestamp) {
            currentMillis = currentTime();
        }
        return currentMillis;
    }

    private long currentTime() {
        return System.currentTimeMillis();
    }
}
