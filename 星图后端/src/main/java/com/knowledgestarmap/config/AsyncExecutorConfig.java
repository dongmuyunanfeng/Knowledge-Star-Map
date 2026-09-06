package com.knowledgestarmap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncExecutorConfig {

    /** 文件解析专用线程池：并发执行「项目理解」与「项目经历」两条 LLM 路径。 */
    @Bean(name = "parseExecutor", destroyMethod = "shutdownNow")
    public ExecutorService parseExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
