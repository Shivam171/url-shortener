package com.shvmpk.url_shortener.util;

import org.springframework.stereotype.Component;

@Component
public class ShortUrlGenerator {

    private final SnowflakeIdGenerator idGenerator;

    public ShortUrlGenerator() {
        long machineId = MachineId.getMachineId();
        this.idGenerator = new SnowflakeIdGenerator(machineId);
    }

    public String generate() {
        return Base62Encoder.encode(idGenerator.nextId());
    }
}
