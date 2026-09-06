package com.knowledgestarmap.service;

import com.knowledgestarmap.common.PageResult;
import com.knowledgestarmap.dto.StudyPlanCreateDTO;
import com.knowledgestarmap.dto.StudyPlanUpdateDTO;
import com.knowledgestarmap.vo.StudyPlanVO;

public interface StudyPlanService {
    StudyPlanVO createPlan(StudyPlanCreateDTO dto, Long userId);
    StudyPlanVO generatePlan(StudyPlanCreateDTO dto, Long userId);
    PageResult<StudyPlanVO> listPlans(Long userId, int page, int pageSize);
    StudyPlanVO getPlan(Long planId, Long userId);
    StudyPlanVO updatePlan(Long planId, Long userId, StudyPlanUpdateDTO dto);
    void deletePlan(Long planId, Long userId);
    void refreshProgress(Long planId, Long userId);
    void completePlan(Long planId, Long userId);
}
