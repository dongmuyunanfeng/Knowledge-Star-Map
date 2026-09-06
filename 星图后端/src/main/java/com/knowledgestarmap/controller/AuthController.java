package com.knowledgestarmap.controller;

import com.knowledgestarmap.common.Result;
import com.knowledgestarmap.dto.UserLoginDTO;
import com.knowledgestarmap.dto.UserRegisterDTO;
import com.knowledgestarmap.service.AuthService;
import com.knowledgestarmap.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody UserRegisterDTO dto) {
        String token = authService.register(dto);
        return Result.ok(token);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody UserLoginDTO dto) {
        String token = authService.login(dto);
        return Result.ok(token);
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.getCurrentUser());
    }

    @PatchMapping("/me")
    public Result<UserVO> updateMe(@RequestBody(required = false) UserRegisterDTO dto) {
        return Result.ok(authService.updateCurrentUser(dto));
    }
}
