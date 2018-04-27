package com.warmmen.core.lock;

public class RedisLockException extends RuntimeException {
    public RedisLockException(String message) {
        super(message);
    }
}
