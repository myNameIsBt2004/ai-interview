package com.aiinterview.manager;

import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.Constants;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.config.AliyunDashScopeProperties;
import com.aiinterview.config.AliyunDashVectorProperties;
import com.aiinterview.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DashScope 文本向量化
 */
@Slf4j
@Component
public class DashScopeEmbeddingManager {

    @Resource
    private AliyunDashScopeProperties dashScopeProperties;

    @Resource
    private AliyunDashVectorProperties dashVectorProperties;

    @PostConstruct
    public void init() {
        if (StrUtil.isNotBlank(dashScopeProperties.getApiKey())) {
            Constants.apiKey = dashScopeProperties.getApiKey();
        }
    }

    public boolean isConfigured() {
        return StrUtil.isNotBlank(dashScopeProperties.getApiKey());
    }

    /**
     * 将文本转为稠密向量（Float 列表，维度与 DashVector Collection 一致）
     */
    public List<Float> embed(String text) {
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "向量化文本不能为空");
        }
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未配置 aliyun.dashscope.apiKey");
        }
        try {
            Constants.apiKey = dashScopeProperties.getApiKey();
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(dashScopeProperties.getEmbeddingModel())
                    .texts(List.of(text.trim()))
                    .parameter("dimension", dashVectorProperties.getDimension())
                    .build();
            TextEmbedding textEmbedding = new TextEmbedding();
            TextEmbeddingResult result = textEmbedding.call(param);
            if (result == null || result.getOutput() == null
                    || result.getOutput().getEmbeddings() == null
                    || result.getOutput().getEmbeddings().isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Embedding 返回为空");
            }
            TextEmbeddingResultItem item = result.getOutput().getEmbeddings().get(0);
            List<Double> embedding = item.getEmbedding();
            if (embedding == null || embedding.isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Embedding 向量为空");
            }
            List<Float> floats = new ArrayList<>(embedding.size());
            for (Double d : embedding) {
                floats.add(d == null ? 0f : d.floatValue());
            }
            return floats;
        } catch (BusinessException e) {
            throw e;
        } catch (NoApiKeyException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "DashScope API Key 无效或未配置");
        } catch (ApiException e) {
            log.error("DashScope Embedding 调用失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Embedding 调用失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("DashScope Embedding 异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Embedding 异常: " + e.getMessage());
        }
    }

    /**
     * 批量向量化（按批调用，单次最多 10 条）
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<List<Float>> all = new ArrayList<>(texts.size());
        int batchSize = 10;
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            all.addAll(embedBatchInternal(batch));
        }
        return all;
    }

    private List<List<Float>> embedBatchInternal(List<String> texts) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未配置 aliyun.dashscope.apiKey");
        }
        try {
            Constants.apiKey = dashScopeProperties.getApiKey();
            List<String> cleaned = texts.stream()
                    .map(t -> StrUtil.blankToDefault(t, " ").trim())
                    .collect(Collectors.toList());
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(dashScopeProperties.getEmbeddingModel())
                    .texts(cleaned)
                    .parameter("dimension", dashVectorProperties.getDimension())
                    .build();
            TextEmbedding textEmbedding = new TextEmbedding();
            TextEmbeddingResult result = textEmbedding.call(param);
            List<TextEmbeddingResultItem> items = result.getOutput().getEmbeddings();
            // DashScope 可能不保证顺序，按 textIndex 排序
            items.sort((a, b) -> Integer.compare(
                    a.getTextIndex() == null ? 0 : a.getTextIndex(),
                    b.getTextIndex() == null ? 0 : b.getTextIndex()));
            List<List<Float>> vectors = new ArrayList<>(items.size());
            for (TextEmbeddingResultItem item : items) {
                List<Float> floats = new ArrayList<>();
                for (Double d : item.getEmbedding()) {
                    floats.add(d == null ? 0f : d.floatValue());
                }
                vectors.add(floats);
            }
            return vectors;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DashScope 批量 Embedding 失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量 Embedding 失败: " + e.getMessage());
        }
    }
}
