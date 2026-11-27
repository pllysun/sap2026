package com.sap.mcp;

import java.util.Arrays;

enum McpScope {
    SCHEDULE("schedule:read", "get_my_schedule", "查询本人的课表。省略学期时使用教务默认学期。"),
    GRADES("grades:read", "get_my_grades", "查询本人指定学期的课程、成绩和学分。"),
    EVALUATIONS("evaluations:read", "get_my_evaluations", "查看本人的待评和已评课程及截止时间。"),
    EXAMS("exams:read", "get_my_exams", "查询本人的考试时间、地点和座位。省略学期时使用教务默认学期。");

    final String value;
    final String tool;
    final String description;

    McpScope(String value, String tool, String description) {
        this.value = value;
        this.tool = tool;
        this.description = description;
    }

    static boolean supports(String value) {
        return Arrays.stream(values()).anyMatch(scope -> scope.value.equals(value));
    }
}
