package com.warmmen.core.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnClass({DefaultRedisLock.class, RedisTemplate.class, StringRedisTemplate.class})
@EnableConfigurationProperties(RedisLockProperty.class)
@Import({RedisLockAspect.class, DefaultRedisLock.class})
public class RedisLockAutoConfiguration {
}
