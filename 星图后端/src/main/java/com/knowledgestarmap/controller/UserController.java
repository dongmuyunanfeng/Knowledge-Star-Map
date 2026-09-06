package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.UserConfigDTO;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.UserConfigService;
import com.knowledgestarmap.vo.UserConfigVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserConfigService userConfigService;

    @GetMapping("/config")
    public Result<UserConfigVO> getConfig() {
        Long userId = UserContext.getUserId();
        return Result.ok(userConfigService.getConfig(userId));
    }

    @PostMapping("/config")
    public Result<Void> updateConfig(@RequestBody UserConfigDTO dto) {
        Long userId = UserContext.getUserId();
        userConfigService.updateConfig(userId, dto);
        return Result.ok();
    }
}
