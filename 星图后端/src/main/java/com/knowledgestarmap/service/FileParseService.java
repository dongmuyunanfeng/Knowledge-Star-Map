package com.knowledgestarmap.service;

import com.knowledgestarmap.entity.FileResourceDO;
import com.knowledgestarmap.entity.ImageOcrDO;
import com.knowledgestarmap.vo.FileParseResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Consumer;

public interface FileParseService {
    FileResourceDO uploadFile(Long userId, MultipartFile file);
    FileParseResult parseFile(Long fileId, Long userId);

    /** 带进度回调的解析：onProgress 接收阶段文案，供 SSE 推送。 */
    FileParseResult parseFileWithProgress(Long fileId, Long userId, Consumer<String> onProgress);

    /** 按 fileId 读取并提取文本内容（zip/pdf/office/图片/纯文本），供项目经历重生成复用。 */
    String extractContent(Long fileId, Long userId);

    ImageOcrDO processOcr(Long fileId, Long userId);
    void deleteFile(Long fileId, Long userId);
}
