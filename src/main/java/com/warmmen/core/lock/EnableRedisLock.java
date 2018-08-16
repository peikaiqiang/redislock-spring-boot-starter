package com.warmmen.core.lock;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({RedisLockAutoConfiguration.class})
public @interface EnableRedisLock {
}
