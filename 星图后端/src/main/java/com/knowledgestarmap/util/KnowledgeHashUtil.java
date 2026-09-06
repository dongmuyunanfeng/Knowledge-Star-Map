package com.knowledgestarmap.util;

import cn.hutool.crypto.digest.DigestUtil;

public class KnowledgeHashUtil {

    private KnowledgeHashUtil() {}

    public static String computeHash(String name, String content) {
        String input = name + content;
        return DigestUtil.md5Hex(input);
    }
}
