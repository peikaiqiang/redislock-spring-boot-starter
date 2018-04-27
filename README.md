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
prefix
suffix
blocked false
error 正在排队中，请稍后！
timeout 500
retry 1
```

- @RedisLock 方法注解
```
prefix
suffix
blocked false
error 正在排队中，请稍后！
timeout 500
retry 1
```

- @RedisLockRequest 方法注解
```
value
```

- @RedisLockParam 方法注解
```
value
```

- @RedisLockable 方法注解
```
value
```
