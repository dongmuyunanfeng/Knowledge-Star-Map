package com.knowledgestarmap.tool;

import com.knowledgestarmap.agent.AgentTool;
import com.knowledgestarmap.agent.ToolExecutionResult;
import com.knowledgestarmap.service.FileParseService;
import com.knowledgestarmap.vo.FileParseResult;
import com.knowledgestarmap.vo.KnowledgeSliceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class FileParseTool implements AgentTool {

    private static final String NAME = "file_parse";
    private static final String DESCRIPTION = "解析用户上传的文件，提取文本内容，进行语义切片，生成知识点列表。返回知识点列表（含内容哈希）、新增领域列表、重复知识点哈希列表。";
    private static final String PARAMETERS_SCHEMA = "{\"type\":\"object\",\"properties\":{\"fileId\":{\"type\":\"integer\",\"description\":\"文件ID（file_resource.id）\"}},\"required\":[\"fileId\"]}";

    private final FileParseService fileParseService;

    public FileParseTool(FileParseService fileParseService) {
        this.fileParseService = fileParseService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public String parametersSchema() {
        return PARAMETERS_SCHEMA;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> parameters, Long userId) {
        Object fileIdObj = parameters.get("fileId");
        if (fileIdObj == null) {
            return ToolExecutionResult.failure("参数fileId不能为空");
        }
        Long fileId;
        try {
            fileId = ((Number) fileIdObj).longValue();
        } catch (Exception e) {
            return ToolExecutionResult.failure("fileId参数类型错误");
        }

        try {
            FileParseResult parseResult = fileParseService.parseFile(fileId, userId);

            if (!parseResult.isParseSuccess()) {
                return ToolExecutionResult.failure(parseResult.getErrorMessage());
            }

            // 解析后的文本转换为知识切片（parseFile内部已完成清理旧数据与入库，此处仅组装返回）
            List<KnowledgeSliceInfo> slices = parseResult.getKnowledgeSlices();
            if (slices == null || slices.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("knowledgeUnits", Collections.emptyList());
                result.put("newDomains", Collections.emptyList());
                result.put("duplicates", Collections.emptyList());
                return ToolExecutionResult.success(result);
            }

            List<Map<String, Object>> knowledgeUnits = new ArrayList<>();
            List<String> newDomains = new ArrayList<>();
            for (KnowledgeSliceInfo slice : slices) {
                Map<String, Object> unit = new LinkedHashMap<>();
                unit.put("tempId", slice.getTempId() != null ? slice.getTempId() : UUID.randomUUID().toString());
                unit.put("name", slice.getName());
                unit.put("tag", slice.getTag());
                unit.put("content", slice.getContent());
                unit.put("hash", com.knowledgestarmap.util.KnowledgeHashUtil.computeHash(slice.getName(), slice.getContent()));
                unit.put("segmentStart", slice.getStartPosition());
                unit.put("segmentEnd", slice.getEndPosition());
                knowledgeUnits.add(unit);
                if (slice.getDomain() != null && !slice.getDomain().isBlank() && !newDomains.contains(slice.getDomain())) {
                    newDomains.add(slice.getDomain());
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("knowledgeUnits", knowledgeUnits);
            result.put("newDomains", newDomains);
            result.put("duplicates", Collections.emptyList());
            result.put("duplicateCount", parseResult.getDuplicateCount());

            return ToolExecutionResult.success(result);

        } catch (Exception e) {
            log.error("文件解析工具执行失败: fileId={}", fileId, e);
            return ToolExecutionResult.failure(e.getMessage());
        }
    }
}
