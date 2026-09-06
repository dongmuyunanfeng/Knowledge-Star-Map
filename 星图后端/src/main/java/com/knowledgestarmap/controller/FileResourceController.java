package com.knowledgestarmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.entity.FileResourceDO;
import com.knowledgestarmap.mapper.FileResourceMapper;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.FileParseService;
import com.knowledgestarmap.service.KnowledgeQueryService;
import com.knowledgestarmap.util.SseUtil;
import com.knowledgestarmap.vo.KnowledgeInfoVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileResourceController {

    private static final Set<String> VALID_FILE_CATEGORIES = Set.of(
            "note", "project", "code", "image", "manual", "other"
    );

    private final FileParseService fileParseService;
    private final FileResourceMapper fileResourceMapper;
    private final KnowledgeQueryService knowledgeQueryService;

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    public FileResourceController(FileParseService fileParseService,
                                   FileResourceMapper fileResourceMapper,
                                   KnowledgeQueryService knowledgeQueryService) {
        this.fileParseService = fileParseService;
        this.fileResourceMapper = fileResourceMapper;
        this.knowledgeQueryService = knowledgeQueryService;
    }

    @PostMapping("/upload")
    public Result<FileResourceDO> upload(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        FileResourceDO uploaded = fileParseService.uploadFile(userId, file);
        return Result.ok(uploaded);
    }

    @GetMapping
    public Result<PageResult<FileResourceDO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.getUserId();
        page = Math.max(1, page);
        pageSize = Math.min(100, Math.max(1, pageSize));

        Page<FileResourceDO> mpPage = fileResourceMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<FileResourceDO>()
                        .eq(FileResourceDO::getUserId, userId)
                        .orderByDesc(FileResourceDO::getCreateTime)
        );

        return Result.ok(PageResult.of(mpPage.getTotal(), page, pageSize, mpPage.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<FileResourceDO> getById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        FileResourceDO file = fileResourceMapper.selectOne(
                new LambdaQueryWrapper<FileResourceDO>()
                        .eq(FileResourceDO::getId, id)
                        .eq(FileResourceDO::getUserId, userId)
                        .eq(FileResourceDO::getIsDeleted, 0)
        );
        if (file == null) {
            return Result.fail(com.knowledgestarmap.enums.BizErrorCode.FILE_NOT_FOUND);
        }
        return Result.ok(file);
    }

    @GetMapping("/{id}/knowledge")
    public Result<List<KnowledgeInfoVO>> getKnowledge(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        // verify file ownership
        FileResourceDO file = fileResourceMapper.selectOne(
                new LambdaQueryWrapper<FileResourceDO>()
                        .eq(FileResourceDO::getId, id)
                        .eq(FileResourceDO::getUserId, userId)
                        .eq(FileResourceDO::getIsDeleted, 0)
        );
        if (file == null) {
            return Result.fail(com.knowledgestarmap.enums.BizErrorCode.FILE_NOT_FOUND);
        }
        List<KnowledgeInfoVO> knowledgeList = knowledgeQueryService.getByFileId(id, userId);
        return Result.ok(knowledgeList);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        FileResourceDO file = fileResourceMapper.selectOne(
                new LambdaQueryWrapper<FileResourceDO>()
                        .eq(FileResourceDO::getId, id)
                        .eq(FileResourceDO::getUserId, userId)
                        .eq(FileResourceDO::getIsDeleted, 0)
        );
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        Path uploadPath = Paths.get(uploadDir).normalize();
        Path filePath = uploadPath.resolve(file.getFilePath()).normalize();
        if (!filePath.startsWith(uploadPath)) {
            log.warn("路径穿越攻击拦截: requested={}, allowed={}", filePath, uploadPath);
            return ResponseEntity.badRequest().build();
        }
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream is = Files.newInputStream(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(is));
        } catch (IOException e) {
            log.error("文件下载失败: fileId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        fileParseService.deleteFile(id, userId);
        return Result.ok();
    }

    @PutMapping("/{id}/category")
    public Result<FileResourceDO> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        String fileCategory = body != null ? body.get("fileCategory") : null;
        if (fileCategory == null || !VALID_FILE_CATEGORIES.contains(fileCategory)) {
            return Result.fail(com.knowledgestarmap.enums.BizErrorCode.PARAM_ERROR, "非法的文件类型");
        }

        FileResourceDO file = fileResourceMapper.selectOne(
                new LambdaQueryWrapper<FileResourceDO>()
                        .eq(FileResourceDO::getId, id)
                        .eq(FileResourceDO::getUserId, userId)
                        .eq(FileResourceDO::getIsDeleted, 0)
        );
        if (file == null) {
            return Result.fail(com.knowledgestarmap.enums.BizErrorCode.FILE_NOT_FOUND);
        }

        file.setFileCategory(fileCategory);
        fileResourceMapper.updateById(file);
        return Result.ok(file);
    }

    @PostMapping("/{id}/parse")
    public Result<Map<String, Object>> reparse(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        com.knowledgestarmap.vo.FileParseResult parseResult = fileParseService.parseFile(id, userId);

        Map<String, Object> data = new HashMap<>();
        data.put("parseSuccess", parseResult.isParseSuccess());
        data.put("extractedKnowledgeCount", parseResult.getExtractedKnowledgeCount());
        data.put("errorMessage", parseResult.getErrorMessage());
        return Result.ok(data);
    }

    @PostMapping("/{id}/parse/stream")
    public void parseStream(@PathVariable Long id, HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        try (PrintWriter writer = response.getWriter()) {
            try {
                com.knowledgestarmap.vo.FileParseResult result = fileParseService.parseFileWithProgress(id, userId,
                        msg -> SseUtil.progress(writer, msg));
                Map<String, Object> summary = new HashMap<>();
                summary.put("parseSuccess", result.isParseSuccess());
                summary.put("extractedKnowledgeCount", result.getExtractedKnowledgeCount());
                summary.put("newKnowledgeCount", result.getNewKnowledgeCount());
                summary.put("duplicateCount", result.getDuplicateCount());
                summary.put("errorMessage", result.getErrorMessage());
                SseUtil.done(writer, summary);
            } catch (Exception e) {
                SseUtil.error(writer, "解析失败：" + e.getMessage());
            }
        } catch (Exception e) {
            log.warn("解析流式异常: fileId={}, err={}", id, e.getMessage());
        }
    }
}
