package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.ResumeGenerateDTO;
import com.knowledgestarmap.dto.ResumeTemplateSaveDTO;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.ResumeService;
import com.knowledgestarmap.util.SseUtil;
import com.knowledgestarmap.vo.ResumeMaterialVO;
import com.knowledgestarmap.vo.ResumeTemplateVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/resume")
public class ResumeMaterialController {

    private final ResumeService resumeService;

    public ResumeMaterialController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public Result<ResumeMaterialVO> getResume() {
        Long userId = UserContext.getUserId();
        return Result.ok(resumeService.getResume(userId));
    }

    @GetMapping("/templates")
    public Result<List<ResumeTemplateVO>> listTemplates() {
        Long userId = UserContext.getUserId();
        return Result.ok(resumeService.listTemplates(userId));
    }

    @PostMapping("/templates")
    public Result<ResumeTemplateVO> saveTemplate(@RequestBody ResumeTemplateSaveDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(resumeService.saveTemplate(userId, dto));
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        resumeService.deleteTemplate(userId, id);
        return Result.ok();
    }

    @PostMapping("/generate")
    public Result<ResumeMaterialVO> generateResume(@RequestBody ResumeGenerateDTO dto) {
        Long userId = UserContext.getUserId();
        boolean forceRegenerate = dto.getForceRegenerate() != null && dto.getForceRegenerate();
        return Result.ok(resumeService.generateResume(userId, forceRegenerate, dto.getTargetNeed(), dto.getRequirement()));
    }

    @PostMapping("/generate/stream")
    public void generateResumeStream(@RequestBody ResumeGenerateDTO dto, HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        boolean forceRegenerate = dto.getForceRegenerate() == null || dto.getForceRegenerate();
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        try (PrintWriter writer = response.getWriter()) {
            try {
                SseUtil.progress(writer, "正在分析知识库与项目经验，生成简历素材…");
                ResumeMaterialVO resume = resumeService.generateResume(userId, forceRegenerate, dto.getTargetNeed(), dto.getRequirement());
                SseUtil.done(writer, resume);
            } catch (Exception e) {
                SseUtil.error(writer, "简历生成失败，请稍后重试");
            }
        } catch (Exception e) {
            log.warn("简历流式生成异常: userId={}, err={}", userId, e.getMessage());
        }
    }
}
