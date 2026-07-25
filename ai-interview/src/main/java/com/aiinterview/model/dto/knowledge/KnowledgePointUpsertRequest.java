package com.aiinterview.model.dto.knowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识点写入请求
 */
@Data
public class KnowledgePointUpsertRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务主键，如 spring_ioc_01
     */
    private String id;

    private String title;

    /**
     * 考察点正文 / 标准答法要点
     */
    private String content;

    /**
     * 技能标签，逗号分隔或空格分隔，如 Java,Spring
     */
    private String skillTags;

    /**
     * 难度：初级 / 中级 / 高级
     */
    private String difficulty;
}
