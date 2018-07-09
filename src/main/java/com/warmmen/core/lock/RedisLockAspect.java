package com.warmmen.core.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author peikaiqiang
 * @CreateTime 2018/04/26
 * @Description  RedisLock 注解拦截器，拦截注解的方法，获取注解配置的参数，多个参数按顺序拼接再加上前后缀作为redis key值放入redis，
 * 放入成功则加锁成功，若第一个进入方法的线程未推出，则第二个进入方法的线程则获取不到锁，从而达到分布式加锁的目的。
 * 按照 RedisLock 的配置，实现相应的功能：包括阻塞，自定义前后缀，阻塞的超时时间，尝试次数，异常信息等。
 */
@Component
@Order(-1)
@Aspect
public class RedisLockAspect {

    // 默认key在redis中存在的超时时间，默认120秒
    private static final Long LONG_DEFAULT_TIMEOUT = 120L;

    private static final String SPLIT = "_";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${redisLock.defaultTimeOut:120}")
    private String DEFAULT_TIMEOUT;

    @Around(value = "@annotation(com.warmmen.core.lock.RedisLock)")
    public Object around(ProceedingJoinPoint jp) throws Throwable {
        // 获取注解配置的锁key值
        Method method = getMethod(jp);
        String key = getLockedKey(jp, method);
        key = SPLIT + key;

        RedisLock redisLock = method.getAnnotation(RedisLock.class);
        key = redisLock.prefix() + key + redisLock.suffix();

        // 锁参数
        Object result = null;
        String error = redisLock.error();
        int retry = redisLock.retry();
        int timeout = redisLock.timeout();
        boolean blocked = redisLock.blocked();
        boolean success = false;

        /**
         * 阻塞方式会在失败后等待一段时间再尝试，尝试次数可以配置，默认为1，最小为1
         * 非阻塞方式会立马获取锁，没有超时时间和尝试次数的配置
         */
        retry = retry > 1 ? retry : 1;

        if (blocked) {
            for (int i = 0; i < retry; i++) {
                success = setKey(key);
                if (success) break;
                if (i + 1 != retry) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(timeout);
                    } catch (Exception e) {
                    }
                }
            }
        } else {
            success = setKey(key);
        }

        /**
         * 加锁成功后执行方法并在退出时解锁
         * 加锁失败则判断是否抛出异常
         */
        if (success) {
            try {
                result = jp.proceed();
            } finally {
//                delKey(key);
            }
        } else {
            throw new RedisLockException(error);
        }

        return result;
    }

    /**
     * redis 设置key
     *
     * @param key
     * @return
     */
    private boolean setKey(String key) {
        return redisTemplate.execute(new RedisCallback<Boolean>() {
            @Override
            public Boolean doInRedis(RedisConnection redisConnection) throws DataAccessException {
                RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
                boolean success = redisConnection.setNX(serializer.serialize(key), serializer.serialize("1"));
                if (success) {
                    try {
                        redisConnection.expire(serializer.serialize(key), Long.parseLong(DEFAULT_TIMEOUT));
                    } catch (NumberFormatException e) {
                        redisConnection.expire(serializer.serialize(key), LONG_DEFAULT_TIMEOUT);
                    }
                }
                return success;
            }
        });
    }

    /**
     * redis 删除 key
     *
     * @param key
     * @return
     */
    private long delKey(String key) {
        return redisTemplate.execute(new RedisCallback<Long>() {
            @Override
            public Long doInRedis(RedisConnection redisConnection) throws DataAccessException {
                RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
                return redisConnection.del(serializer.serialize(key));
            }
        });
    }

    private Method getMethod(ProceedingJoinPoint jp) throws NoSuchMethodException {
        Signature signature = jp.getSignature();
        MethodSignature ms = (MethodSignature) signature;
        Method method = jp.getTarget().getClass().getMethod(ms.getName(), ms.getParameterTypes());
        return method;
    }

    /**
     * 获取锁的key值
     * 获取 RedisLockRequest 对应的 HttpServletRequest 请求参数的值
     * 获取 RedisLockParam 对应参数的值
     *
     * @param jp
     * @param method
     * @return
     * @throws Throwable
     */
    private String getLockedKey(ProceedingJoinPoint jp, Method method) throws Throwable {
        StringBuilder key = new StringBuilder();

        // 获取 RedisLockRequest 对应的 HttpServletRequest 请求参数的值
        RedisLockRequest redisLockRequest = method.getAnnotation(RedisLockRequest.class);

        if (null != redisLockRequest) {
            String[] names = redisLockRequest.value();

            if (names != null && names.length > 0) {
                for (String name : names) {
                    if (!StringUtils.isEmpty(name)) {
                        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                                .getRequestAttributes()).getRequest();
                        Object value = request.getParameter(name);

                        if (null != value) {
                            key.append(value.toString()).append(SPLIT);
                        }
                    }
                }
            }
        }

        /**
         * 获取 RedisLockParam 对应参数的值
         */
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = jp.getArgs()[i];
            RedisLockParam redisLockParam = parameter.getAnnotation(RedisLockParam.class);

            if (null != redisLockParam) {
                String name = redisLockParam.value();

                /**
                 * 默认返回参数本身的toString()作为锁的key值
                 * 若配置了name：
                 * 当参数为map时，返回map中key为name的值
                 * 否则返回bean中name名称的字段对应的值
                 *
                 */
                if (StringUtils.isEmpty(name)) {
                    if (arg instanceof RedisLockable) {
                        RedisLockable lockable = (RedisLockable) arg;
                        key.append(lockable.key()).append(SPLIT);
                    } else {
                        key.append(arg.toString()).append(SPLIT);
                    }
                } else {
                    if (arg instanceof Map) {
                        Map map = (Map) arg;
                        if (map.containsKey(name)) {
                            key.append(map.get(name).toString()).append(SPLIT);
                        }
                    } else {
                        Field field = parameter.getType().getDeclaredField(name);
                        if (null != field) {
                            field.setAccessible(true);
                            Object obj = field.get(arg);
                            key.append(obj.toString()).append(SPLIT);
                        }
                    }
                }
            }
        }

        // 如果获取的最终key值为空，会抛出异常
        if (StringUtils.isEmpty(key.toString())) {
            throw new IllegalArgumentException("RedisLock未获取到有效的锁参数！");
        }

        return key.toString();
    }
}
