package com.knowledgestarmap.security;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return userId;
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
