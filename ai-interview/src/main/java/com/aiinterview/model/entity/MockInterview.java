package com.aiinterview.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 模拟面试
 */
@TableName(value = "mock_interview")
@Data
public class MockInterview implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workExperience;

    private String jobPosition;

    private String difficulty;

    /** 面试类型，如综合面试 */
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

    /** 综合得分 */
    private Integer score;

    /** 实际面试时长（分钟） */
    private Integer durationMinutes;

    /** 结构化评估报告 JSON */
    private String reportJson;

    private Date startTime;

    /**
     * 消息列表（JSON）
     */
    private String messages;

    /**
     * 状态（0-待开始、1-进行中、2-已结束）
     */
    private Integer status;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
