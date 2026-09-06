package com.knowledgestarmap.service;

import com.knowledgestarmap.dto.UserLoginDTO;
import com.knowledgestarmap.dto.UserRegisterDTO;
import com.knowledgestarmap.vo.UserVO;

public interface AuthService {

    /** 注册，返回 JWT token */
    String register(UserRegisterDTO dto);

    /** 登录，返回 JWT token */
    String login(UserLoginDTO dto);

    /** 获取当前用户信息 */
    UserVO getCurrentUser();

    /** 更新当前用户资料 */
    UserVO updateCurrentUser(UserRegisterDTO dto);
}
