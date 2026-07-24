package com.aiinterview.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 简历解析结果
 */
@Data
public class ResumeParseVO implements Serializable {

    private String resumeName;

    /** 简历原文（截断后） */
    private String resumeText;

    private String personalDesc;

    private String yearsOfExperience;

    private String coreSkills;

    private String projectExperience;

    private static final long serialVersionUID = 1L;
}
