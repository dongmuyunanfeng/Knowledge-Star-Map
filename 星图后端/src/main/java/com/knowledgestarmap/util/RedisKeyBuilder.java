package com.knowledgestarmap.util;

public class RedisKeyBuilder {

    private RedisKeyBuilder() {}

    public static String agentSession(String sessionId) {
        return "agent:session:" + sessionId;
    }

    public static String starMap(Long userId) {
        return "star:map:" + userId;
    }

    public static String userConfig(Long userId) {
        return "user:config:" + userId;
    }

    public static String searchResult(Long userId, String keywordHash) {
        return "search:result:" + userId + ":" + keywordHash;
    }

    public static String domainList(Long userId) {
        return "domain:list:" + userId;
    }

    public static String rateLimitPerMinute(Long userId) {
        return "agent:rate:minute:" + userId;
    }

    public static String rateLimitPerDay(Long userId) {
        return "agent:rate:day:" + userId;
    }

    public static String layoutLock(Long userId) {
        return "star:layout:lock:" + userId;
    }

    /** 失效锁专用键，不能与 starMap() 缓存键复用：writeCache 会永久写缓存键，导致 SET NX 永远失败 */
    public static String starMapInvalidateLock(Long userId) {
        return "star:map:invalidate:lock:" + userId;
    }
}
