package com.aiinterview.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 面试评估报告
 */
@Data
public class MockInterviewReportVO implements Serializable {

    private Long id;

    private String interviewType;

    private String jobPosition;

    private String difficulty;

    private String companyName;

    private String interviewer;

    private Integer score;

    private Integer durationMinutes;

    private Integer questionCount;

    private Date startTime;

    private Integer status;

    /** 综合点评 */
    private String summary;

    private List<String> observations;

    private List<String> suggestions;

    private List<AbilityItem> abilities;

    private List<String> strengths;

    private List<String> improvements;

    private List<SkillItem> skillMatrix;

    private List<QaItem> qaAnalysis;

    /** 学习规划总述（兼容旧数据） */
    private String learningPlan;

    private List<LearningFocusItem> learningFocus;

    private List<RoadmapItem> roadmap;

    private List<ChatItem> messages;

    @Data
    public static class AbilityItem implements Serializable {
        private String name;
        private Integer score;
        private String tip;
    }

    @Data
    public static class SkillItem implements Serializable {
        private String name;
        private Integer score;
        private String evaluation;
        private String advice;
        private List<String> resources;
    }

    @Data
    public static class QaItem implements Serializable {
        private String question;
        private String answer;
        private Integer score;
        private String analysis;
        private List<String> followUps;
        private String comment;
        private String reference;
        private String referenceAnswer;
        private List<String> relatedQuestions;
    }

    @Data
    public static class LearningFocusItem implements Serializable {
        private String name;
        private String priority;
    }

    @Data
    public static class RoadmapItem implements Serializable {
        private String stage;
        private String duration;
        private List<String> items;
    }

    @Data
    public static class ChatItem implements Serializable {
        private String role;
        private String content;
    }

    private static final long serialVersionUID = 1L;
}
