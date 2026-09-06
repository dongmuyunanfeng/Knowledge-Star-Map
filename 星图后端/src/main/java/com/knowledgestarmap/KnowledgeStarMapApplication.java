package com.knowledgestarmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
public class KnowledgeStarMapApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeStarMapApplication.class, args);
    }
}
