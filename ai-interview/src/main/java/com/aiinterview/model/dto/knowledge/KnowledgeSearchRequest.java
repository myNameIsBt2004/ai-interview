package com.aiinterview.model.dto.knowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识点检索调试请求
 */
@Data
public class KnowledgeSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;

    /**
     * 可选，不传则用配置 topK
     */
    private Integer topK;
}
