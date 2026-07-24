package com.aiinterview.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建模拟面试请求
 */
@Data
public class MockInterviewAddRequest implements Serializable {

    private String workExperience;

    private String jobPosition;

    private String difficulty;

    private String interviewType;

    private Integer salaryMin;

    private Integer salaryMax;

    private String jobDescription;

    private String companyName;

    private String personalDesc;

    private String yearsOfExperience;

    private String coreSkills;

    private String projectExperience;

    private String resumeName;

    private String resumeText;

    private String focus;

    /** 计划面试时长（分钟） */
    private Integer duration;

    private String interviewer;

    private static final long serialVersionUID = 1L;
}
