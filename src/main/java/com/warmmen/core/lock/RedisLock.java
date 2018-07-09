package com.warmmen.core.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author peikaiqiang
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RedisLock {

    /**
     * 前缀
     *
     * @return 前缀
     */
    String prefix() default "";

    /**
     * 后缀
     *
     * @return 后缀
     */
    String suffix() default "";

    /**
     * 是否阻塞获取锁
     *
     * @return 是否阻塞获取锁
     */
    boolean blocked() default false;

    /**
     * 抛出异常时，抛出的信息
     *
     * @return 错误信息
     */
    String error() default "正在排队中，请稍后！";

    /**
     * 阻塞获取锁的超时时间
     *
     * @return 阻塞获取锁的超时时间，单位毫秒，默认500毫秒
     */
    int timeout() default 500;

    /**
     * 重试次数
     *
     * @return 重试次数，默认1次，最小1次
     */
    int retry() default 1;
}