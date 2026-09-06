package com.knowledgestarmap.config;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.service.AgentToolDispatcherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class AgentToolConfig implements ApplicationRunner {

    private final AgentToolDispatcherService dispatcher;
    private final List<AgentTool> tools;

    public AgentToolConfig(AgentToolDispatcherService dispatcher, List<AgentTool> tools) {
        this.dispatcher = dispatcher;
        this.tools = tools;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (AgentTool tool : tools) {
            dispatcher.registerTool(tool);
            log.info("注册Agent工具: {}", tool.name());
        }
        log.info("Agent工具注册完成，共{}个工具", tools.size());
    }
}
