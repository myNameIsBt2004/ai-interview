package com.aiinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 DashVector 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.dashvector")
public class AliyunDashVectorProperties {

    /**
     * 是否启用知识点检索与入库
     */
    private boolean enabled = false;

    private String apiKey = "";

    /**
     * Cluster Endpoint，如 vrs-cn-xxx.dashvector.cn-hangzhou.aliyuncs.com
     */
    private String endpoint = "";

    /**
     * 集合名。推荐 interview_rag（由 HTTP API 按单向量 dimension 创建）
     */
    private String collection = "interview_rag";

    /**
     * 须与 Embedding 输出维度一致
     */
    private int dimension = 1024;

    private int topK = 5;

    /**
     * 多向量 Collection 的稠密向量字段名（不要用 proxima_vector）。
     * 当前集群下查询/写入都必须带该字段名。
     */
    private String vectorField = "embedding";
}
