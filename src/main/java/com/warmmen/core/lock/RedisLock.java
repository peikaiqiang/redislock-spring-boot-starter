package com.warmmen.core.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RedisLock {

    /**
     * 前缀
     *
     * @return
     */
    String prefix() default "";

    /**
     * 后缀
     *
     * @return
     */
    String suffix() default "";

    /**
     * 是否阻塞获取锁
     *
     * @return
     */
    boolean blocked() default false;

    /**
     * 抛出异常时，抛出的信息
     *
     * @return
     */
    String error() default "正在排队中，请稍后！";

    /**
     * 阻塞获取锁的超时时间，单位毫秒，默认500毫秒
     *
     * @return
     */
    int timeout() default 500;

    /**
     * 重试次数，默认1次，最小1次
     *
     * @return
     */
    int retry() default 1;
}