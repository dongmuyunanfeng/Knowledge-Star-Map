package com.knowledgestarmap.controller;

import com.knowledgestarmap.agent.AgentResult;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.AgentSessionContinueDTO;
import com.knowledgestarmap.dto.AgentSessionCreateDTO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.AgentSessionService;
import com.knowledgestarmap.util.SseUtil;
import com.knowledgestarmap.vo.AgentSessionVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agent/sessions")
public class AgentSessionController {

    private final AgentSessionService agentSessionService;

    public AgentSessionController(AgentSessionService agentSessionService) {
        this.agentSessionService = agentSessionService;
    }

    @PostMapping
    public Result<AgentResult> createSession(@RequestBody AgentSessionCreateDTO dto) {
        Long userId = UserContext.getUserId();
        if (dto.getQuery() == null || dto.getQuery().isBlank()) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "query不能为空");
        }
        AgentResult result = agentSessionService.createSession(userId, dto.getQuery());
        return Result.ok(result);
    }

    @PostMapping("/{id}/continue")
    public Result<AgentResult> continueSession(@PathVariable String id,
                                               @RequestBody AgentSessionContinueDTO dto) {
        Long userId = UserContext.getUserId();
        if (dto.getQuery() == null || dto.getQuery().isBlank()) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "query不能为空");
        }
        AgentResult result = agentSessionService.continueSession(id, userId, dto.getQuery());
        return Result.ok(result);
    }

    @PostMapping("/stream")
    public void createSessionStream(@RequestBody AgentSessionCreateDTO dto, HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        if (dto.getQuery() == null || dto.getQuery().isBlank()) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "query不能为空");
        }
        prepareSse(response);
        try (PrintWriter writer = response.getWriter()) {
            try {
                // 先推送 sessionId，让前端在请求发起瞬间即可持久化会话标识，避免中途切换会话导致新会话丢失
                String sessionId = UUID.randomUUID().toString();
                SseUtil.event(writer, "session", Map.of("sessionId", sessionId));
                AgentResult result = agentSessionService.createSession(userId, dto.getQuery(),
                        delta -> SseUtil.delta(writer, delta), sessionId);
                SseUtil.done(writer, result);
            } catch (BizException e) {
                SseUtil.error(writer, e.getCode(), e.getMessage());
            } catch (Exception e) {
                SseUtil.error(writer, "AI服务暂时不可用，请稍后重试");
            }
        } catch (Exception e) {
            log.warn("流式会话创建异常: userId={}, err={}", userId, e.getMessage());
        }
    }

    @PostMapping("/{id}/continue/stream")
    public void continueSessionStream(@PathVariable String id,
                                      @RequestBody AgentSessionContinueDTO dto,
                                      HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        if (dto.getQuery() == null || dto.getQuery().isBlank()) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "query不能为空");
        }
        prepareSse(response);
        try (PrintWriter writer = response.getWriter()) {
            try {
                AgentResult result = agentSessionService.continueSession(id, userId, dto.getQuery(),
                        delta -> SseUtil.delta(writer, delta));
                SseUtil.done(writer, result);
            } catch (BizException e) {
                SseUtil.error(writer, e.getCode(), e.getMessage());
            } catch (Exception e) {
                SseUtil.error(writer, "AI服务暂时不可用，请稍后重试");
            }
        } catch (Exception e) {
            log.warn("流式会话继续异常: sessionId={}, err={}", id, e.getMessage());
        }
    }

    private void prepareSse(HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
    }

    @DeleteMapping("/{id}")
    public Result<Void> terminateSession(@PathVariable String id) {
        Long userId = UserContext.getUserId();
        agentSessionService.terminateSession(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/pin")
    public Result<Void> pinSession(@PathVariable String id) {
        Long userId = UserContext.getUserId();
        agentSessionService.pinSession(id, userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}/pin")
    public Result<Void> unpinSession(@PathVariable String id) {
        Long userId = UserContext.getUserId();
        agentSessionService.unpinSession(id, userId);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<AgentSessionVO> getSession(@PathVariable String id) {
        Long userId = UserContext.getUserId();
        AgentSessionVO session = agentSessionService.getSession(id, userId);
        return Result.ok(session);
    }

    @GetMapping
    public Result<PageResult<AgentSessionVO>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getUserId();
        PageResult<AgentSessionVO> result = agentSessionService.listSessions(userId, page, pageSize);
        return Result.ok(result);
    }
}
