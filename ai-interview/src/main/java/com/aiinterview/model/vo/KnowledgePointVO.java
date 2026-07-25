package com.aiinterview.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 检索到的知识点
 */
@Data
public class KnowledgePointVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String title;

    private String content;

    private String skillTags;

    private String difficulty;

    /**
     * 相似度分数（DashVector 返回，越大通常越相关，取决于度量）
     */
    private Float score;
}
