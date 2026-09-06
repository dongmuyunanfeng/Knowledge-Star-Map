package com.knowledgestarmap.vo;

import lombok.Data;

import java.util.List;

@Data
public class AgentRoundVO {
    private Integer roundNo;
    private String agentThought;
    private List<ToolCallVO> toolCalls;
    private List<ToolObservationVO> toolObservations;
}
