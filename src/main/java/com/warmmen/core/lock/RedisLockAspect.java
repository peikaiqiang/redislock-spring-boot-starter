package com.warmmen.core.lock;

import com.warmmen.core.lock.annotation.RedisLock;
import com.warmmen.core.lock.annotation.RedisLockParam;
import com.warmmen.core.lock.annotation.RedisLockRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MapAccessor;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * @author peikaiqiang
 * @version create at 2018/04/26
 * @see RedisLock 注解拦截器，拦截注解的方法，获取注解配置的参数，多个参数按顺序拼接再加上前后缀作为redis key值放入redis，
 * 放入成功则加锁成功，若第一个进入方法的线程未推出，则第二个进入方法的线程则获取不到锁，从而达到分布式加锁的目的。
 * 按照 RedisLock 的配置，实现相应的功能：包括阻塞，自定义key，后缀，阻塞的超时时间，异常信息等。
 */
@Component
@Order(-1)
@Aspect
public class RedisLockAspect {

    private static final String SPLIT = "_";
    private final ExpressionParser parser = new SpelExpressionParser();

    @Autowired
    private RedisLockProperty redisLockProperty;

    @Autowired
    private DefaultRedisLock defaultRedisLock;

    @Around(value = "@annotation(com.warmmen.core.lock.annotation.RedisLock)")
    public Object around(ProceedingJoinPoint jp) throws Throwable {
        // 获取注解配置的锁key值
        Method method = getMethod(jp);
        String key = getLockedKey(jp, method);

        RedisLock redisLock = method.getAnnotation(RedisLock.class);

        String error = redisLock.error();
        long timeout = redisLock.timeout();
        boolean blocked = redisLock.blocked();

        if (StringUtils.isEmpty(error)) {
            error = redisLockProperty.getDefaultError();
        }

        if (timeout == 0L) {
            timeout = Long.parseLong(redisLockProperty.getTimeout());
        }

        /**
         * 阻塞方式会在失败后等待一段时间再重试，第一次重试等待时间为50毫秒，此后每失败一次等待时间翻倍。直到达到timeout的时间或者redis key存活的时间结束。
         * 非阻塞方式会立马获取一次锁
         */
        boolean success = false;
        if (blocked) {
            success = defaultRedisLock.tryLock(key, timeout);
        } else {
            success = defaultRedisLock.tryLock(key);
        }

        /**
         * 加锁成功后执行方法并在退出时解锁
         * 加锁失败则判断是否抛出异常
         */
        Object result = null;
        if (success) {
            try {
                result = jp.proceed();
            } finally {
                defaultRedisLock.unlock(key);
            }
        } else {
            throw new RedisLockException(error);
        }

        return result;
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
                 * 则返回参数的SpEl表达式的值
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
                    key.append(parse(arg, name));
                }
            }
        }

        // 如果获取的最终key值为空，会抛出异常
        if (StringUtils.isEmpty(key.toString())) {
            throw new IllegalArgumentException("RedisLock未获取到有效的锁参数！");
        }

        // 加上前后缀
        RedisLock redisLock = method.getAnnotation(RedisLock.class);

        key = new StringBuilder()
                .append(redisLock.value())
                .append(SPLIT)
                .append(key.toString())
                .append(redisLock.suffix());

        return key.toString();
    }

    private String parse(Object root, String exp) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(root);
            context.addPropertyAccessor(new MapAccessor());
            Expression expression = parser.parseExpression("#{" + exp + "}", ParserContext.TEMPLATE_EXPRESSION);
            Object cal = expression.getValue(context);
            return cal.toString();
        } catch (Exception e) {
            throw new RedisLockException("Unsupported redis lock param value: " + exp + ".");
        }
    }
}
