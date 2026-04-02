package com.sap.vo;

import lombok.Data;

@Data
public class UserVO {
    private Long id;
    private String studentId;
    private String name;
    private String nickname;
    private Integer gender;
    private String qq;
    private String grade;
    private String avatar;
    private Integer status;
}
