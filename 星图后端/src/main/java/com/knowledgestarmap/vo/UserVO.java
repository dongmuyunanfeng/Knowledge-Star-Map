package com.knowledgestarmap.vo;

import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String studyDirection;
    private String jobTarget;
    private String email;
}
