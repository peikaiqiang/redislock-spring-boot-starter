# RedisLock

### 简介
RedisLock 是基于redis实现的分布式注解锁，原理很简单，就是在redis里面setnx一个key，如果这个key不存在，则加锁成功，多线程环境下再次进入这个方法就会加锁失败。
代码是基于springboot，利用注解和切面实现，对项目没有依赖。切面的order是-1，也就是在spring事务切面的外层。因为都是注解实现，所以开箱即用，对原项目的依赖只有spring-boot-starter-data-redis，也就是redis的配置。

### 配置
- pom 引入redis
```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```
- redis 配置，具体参考官方文档
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
### 使用
- @RedisLock 方法注解
```
方法加锁的标志，方法必须实现jdk接口，原理同事务。
prefix 前缀，redis中存的key值附加的前缀。
suffix 后缀，redis中存的key值附加的后缀。
blocked 是否阻塞获取锁，默认否。非阻塞获取会立马返回结果，阻塞获取会在失败时休眠一段时间再尝试获取，尝试次数可以配置，默认1次。
error 获取锁失败时抛出的异常信息，默认："抛出的信息正在排队中，请稍后！"
timeout 阻塞获取锁的超时时间，单位毫秒，默认500毫秒。
retry 阻塞获取时，重试次数，默认1次，最小1次。
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
当value非空时：如果参数是Map，则返回Map中key为value的值；如果参数是object，则返回value名称的字段。
```

- RedisLockable 接口
```
自定义获取参数的锁Key
```

### 锁key的顺序
先从 @RedisLockRequest配置的请求参数中获取，配置多个时，按配置的顺序获取，再从 @RedisLockParam中获取，多个时以下划线(_)分隔。如果取得的key为空，则会抛出异常，如果非空，则加上配置的前后缀。

### 例子
