package com.aiinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 DashScope（Embedding）配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.dashscope")
public class AliyunDashScopeProperties {

    /**
     * DashScope / 百炼 API Key
     */
    private String apiKey = "";

    /**
     * Embedding 模型名
     */
    private String embeddingModel = "text-embedding-v3";
}
