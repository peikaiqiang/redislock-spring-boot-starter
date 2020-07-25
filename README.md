# redislock-spring-boot-starter

### 简介
redislock-spring-boot-starter 是基于redis实现的分布式注解锁，原理很简单，就是在redis里面setnx一个key，如果这个key不存在，则加锁成功，多线程环境下再次进入这个方法就会加锁失败。项目以spring-boot-starter的形式发布，引入方便。既可以以加注解的方式实现方法如入口层面的加锁，也可以注入相应的加锁类，以嵌入代码的形式加锁。作者更推荐使用注解的形式，注解切面的order是-1，也就是在spring事务切面的外层。开箱即用，只需要配置spring redis即可，该项目已经在作者的两家公司应用了，觉得不错记得点赞！

**关于timeout的设置** 小于50毫秒则会直接休眠timeout的时间。大于50时，则第一次休眠50毫秒，此后休眠时间逐次翻倍，最后一次休眠的时间为剩余待休眠时间，即保证总休眠时间等于timeout的值。

**关于key到期后的安全问题** 假如第一个线程执行方法超时，redis key到期后，第二个线程可以进入重新加锁。第一个超时方法执行完后是不会把第二个线程加的锁释放掉的，程序已经做了处理。怎么处理第二个线程进入的问题，框架没有处理，依赖使用者做好业务幂等处理。全局超时时间是90秒，可以根据业务自行调整，也可以在单个注解上设置单次的超时时间。另外相比手动加锁，不会有遗漏释放锁的风险。

### 命名更新
项目名称之前是RedisLock，考虑到用spring-boot-starter比较方便，所以使用官方推荐的命名方法。原项目1.0.0版本的jar包maven中央库，强烈建议更换新项目的maven dependency。

### 配置
- SpringBootApplication 引入注解 @EnableRedisLock
```
@EnableRedisLock
@SpringBootApplication
public class Application() {
}
```

- pom 引入redis
```
<dependency>
    <groupId>com.warmmen</groupId>
    <artifactId>redislock-spring-boot-starter</artifactId>
</dependency>
```
- redis 配置，具体参考springboot官方文档
```
spring:
  redis:
      host: localhost
      port: 6379
      password:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: 10000
```
### 配置文件属性
```
redis-lock.timeout 全局默认阻塞获取锁的超时时间，单位毫秒，默认500。
redis-lock.prefix 所有加锁的key的前缀，默认为 "REDIS_LOCK_" 。
redis-lock.defaultError 加锁失败抛出的异常信息，默认 "The lock has been occupied.re"。
redis-lock.liveTime 锁持续的时间，单位毫秒，默认90000。锁失效后，其他线程可以进入该锁，设置需谨慎。
```

### 注解方式
- @RedisLock 方法注解
```
方法加锁的标志，方法必须实现jdk接口，原理同事务。
value，redis中存的key值。
suffix 后缀，redis中存的key值附加的后缀。
blocked 是否阻塞获取锁，默认否，非阻塞获取会立马返回结果。如果为true，则在第一次获取锁失败后，会多次休眠尝试重新获取锁。
error 获取锁失败时抛出的异常信息，默认抛出的信息: "The lock has been occupied."
timeout 阻塞获取锁的超时时间，单位毫秒，如不设值，则取全局的默认超时时间，默认500毫秒。也可以在 application.yml配置文件中修改redis-lock.timeout的值。如有设值，则该注解修饰的方法以timeout值作为超时时间。
```

- @RedisLockRequest 方法注解
```
获取Http请求参数的值
value 参数的名称
```

- @RedisLockParam 参数注解
```
获取参数的值
value
当value为空时：如果参数实现了RedisLockable接口，则获取RedisLockable接口的key()，否则返回参数的toString()作为redis锁的key。
当value非空时：以SpEL表达式获取参数中的字段值，假如参数是Map类型，value=“name”, 则返回Map中key为name的值；如果参数是object，则返回name字段的toString(), 假如name字段还是个object，也可以用 “name.filed” 取出name中field字段的值，以此类推（Map 也是相同原则）。
```

- RedisLockable 接口
```
自定义获取参数的锁Key
```
### java 代码方式
```
@Autowired
DefaultRedisLock defaultRedisLock;

void doSomething() {
    try {
        boolean success = defaultRedisLock.tryLock("DO_SOMETHING", 100);
        
        if (success) {
            // do something
        }
    } finally {
        // unlock in finally
        defaultRedisLock.unlock("DO_SOMETHING");
    }
}

tryLock 和 lock 区别？
lock加锁失败会抛出RedisLockException异常，tryLock不会。
tryLock 可以加阻塞时间。
```

### 锁key的拼接顺序
1. redis-lock.prefix 的值。
2. @RedisLockRequest 配置的请求参数中获取，配置多个时，按配置的顺序获取。
3. @RedisLockParam中获取，多个时以下划线(_)分隔。
4. suffix 的值。

如果第2，3步取值都为空，则会抛出异常。

### 例子
```
@RedisLock(vakue = "Test_Method", suffix = "Suf", blocked = true, timeout = 1000, error = "加锁失败，请重试！")
@RedisLockRequest({"arg1", "arg2"})
public void test(@RedisLockParam param1,
                 @RedisLockParam("name") Map<String, Object> param2)

如果获取锁成功，则key的值为：REDIS_LOCK_Test_Method_arg1_arg2_param1_name_Suf，锁的持续时间时90秒
如果获取锁失败，抛出异常："加锁失败，请重试！"
```
