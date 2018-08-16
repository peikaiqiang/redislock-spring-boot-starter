package com.warmmen.core.lock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("redis-lock")
public class RedisLockProperty {

    private String defaultError = "The lock has been occupied.";

    private String liveTime = "90000";

    private String timeout = "500";

    private String prefix = "REDIS_LOCK_";

    public String getDefaultError() {
        return defaultError;
    }

    public void setDefaultError(String defaultError) {
        this.defaultError = defaultError;
    }

    public String getLiveTime() {
        return liveTime;
    }

    public void setLiveTime(String liveTime) {
        this.liveTime = liveTime;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }
}
