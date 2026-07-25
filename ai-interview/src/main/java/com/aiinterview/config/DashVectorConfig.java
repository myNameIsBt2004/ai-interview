package com.aiinterview.config;

import cn.hutool.core.util.StrUtil;
import com.aliyun.dashvector.DashVectorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DashVector 客户端（仅在 enabled=true 时尝试创建；失败不阻断面试主流程）
 */
@Slf4j
@Configuration
public class DashVectorConfig {

    @Bean
    @ConditionalOnProperty(prefix = "aliyun.dashvector", name = "enabled", havingValue = "true")
    public DashVectorClient dashVectorClient(AliyunDashVectorProperties properties) {
        String endpoint = normalizeEndpoint(properties.getEndpoint());
        if (StrUtil.isBlank(properties.getApiKey()) || StrUtil.isBlank(endpoint)) {
            log.warn("aliyun.dashvector.enabled=true，但 apiKey/endpoint 未配置，跳过 DashVector 初始化");
            return null;
        }
        if (!looksLikeValidEndpoint(endpoint)) {
            log.warn("aliyun.dashvector.endpoint 无效（当前值: {}）。请到 DashVector 控制台 → Cluster 详情 "
                    + "复制 Endpoint，形如 vrs-cn-xxxxx.dashvector.cn-hangzhou.aliyuncs.com，"
                    + "不要保留示例占位符，也不要带 https://。已跳过初始化，面试可正常启动。", endpoint);
            return null;
        }
        try {
            log.info("初始化 DashVector Client, endpoint={}, collection={}",
                    endpoint, properties.getCollection());
            return new DashVectorClient(properties.getApiKey().trim(), endpoint);
        } catch (Exception e) {
            log.warn("DashVector Client 初始化失败（{}），已跳过；请检查 endpoint/apiKey。面试主流程不受影响。",
                    e.getMessage());
            return null;
        }
    }

    /**
     * 去掉 https:// 与尾部斜杠，SDK 只要主机名
     */
    static String normalizeEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint)) {
            return "";
        }
        String e = endpoint.trim();
        if (e.startsWith("https://")) {
            e = e.substring("https://".length());
        } else if (e.startsWith("http://")) {
            e = e.substring("http://".length());
        }
        while (e.endsWith("/")) {
            e = e.substring(0, e.length() - 1);
        }
        return e;
    }

    static boolean looksLikeValidEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint)) {
            return false;
        }
        // 占位符或中文说明
        if (endpoint.contains("你的") || endpoint.contains("Cluster_Endpoint") || endpoint.contains("示例")) {
            return false;
        }
        // 至少是带点的主机名
        return endpoint.contains(".") && !endpoint.contains(" ");
    }
}
