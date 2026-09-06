package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.StudyPlanCreateDTO;
import com.knowledgestarmap.dto.StudyPlanUpdateDTO;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.StudyPlanService;
import com.knowledgestarmap.util.SseUtil;
import com.knowledgestarmap.vo.StudyPlanVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;

@Slf4j
@RestController
@RequestMapping("/api/study-plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @GetMapping
    public Result<PageResult<StudyPlanVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.getUserId();
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));
        return Result.ok(studyPlanService.listPlans(userId, page, pageSize));
    }

    @GetMapping("/{id}")
    public Result<StudyPlanVO> getById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.ok(studyPlanService.getPlan(id, userId));
    }

    @PostMapping
    public Result<StudyPlanVO> create(@RequestBody StudyPlanCreateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(studyPlanService.createPlan(dto, userId));
    }

    @PostMapping("/generate")
    public void generate(@RequestBody StudyPlanCreateDTO dto, HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        try (PrintWriter writer = response.getWriter()) {
            SseUtil.progress(writer, "正在分析已掌握知识点并生成学习规划…");
            try {
                StudyPlanVO plan = studyPlanService.generatePlan(dto, userId);
                SseUtil.done(writer, plan);
            } catch (com.knowledgestarmap.exception.BizException e) {
                SseUtil.error(writer, e.getCode(), e.getMessage());
            } catch (Exception e) {
                SseUtil.error(writer, "学习规划生成失败，请稍后重试");
            }
        } catch (Exception e) {
            log.warn("学习规划流式生成异常: userId={}, err={}", userId, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<StudyPlanVO> update(@PathVariable Long id, @RequestBody StudyPlanUpdateDTO dto) {
        Long userId = UserContext.getUserId();
        return Result.ok(studyPlanService.updatePlan(id, userId, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        studyPlanService.deletePlan(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/progress")
    public Result<Void> refreshProgress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        studyPlanService.refreshProgress(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/complete")
    public Result<Void> completePlan(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        studyPlanService.completePlan(id, userId);
        return Result.ok();
    }
}
