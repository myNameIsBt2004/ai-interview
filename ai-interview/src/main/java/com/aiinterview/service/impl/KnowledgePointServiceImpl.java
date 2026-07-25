package com.aiinterview.service.impl;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.config.AliyunDashVectorProperties;
import com.aiinterview.exception.BusinessException;
import com.aiinterview.exception.ThrowUtils;
import com.aiinterview.manager.DashScopeEmbeddingManager;
import com.aiinterview.manager.DashVectorHttpClient;
import com.aiinterview.model.dto.knowledge.KnowledgePointUpsertRequest;
import com.aiinterview.model.vo.KnowledgePointVO;
import com.aiinterview.service.KnowledgePointService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点入库与检索（DashVector HTTP API + DashScope Embedding）
 */
@Slf4j
@Service
public class KnowledgePointServiceImpl implements KnowledgePointService {

    private static final String SAMPLE_PATH = "knowledge/seed-sample.json";

    @Resource
    private AliyunDashVectorProperties dashVectorProperties;

    @Resource
    private DashScopeEmbeddingManager embeddingManager;

    @Resource
    private DashVectorHttpClient dashVectorHttpClient;

    @Override
    public boolean isAvailable() {
        return dashVectorProperties.isEnabled()
                && dashVectorHttpClient.isConfigured()
                && embeddingManager.isConfigured();
    }

    @Override
    public void upsert(KnowledgePointUpsertRequest request) {
        ensureAvailableForWrite();
        validateUpsert(request);
        ensureCollection();
        List<Float> vector = embeddingManager.embed(buildEmbedText(request));
        Map<String, Object> doc = DashVectorHttpClient.buildDoc(
                request.getId().trim(), vector, toFields(request));
        dashVectorHttpClient.upsertDocs(dashVectorProperties.getCollection(), List.of(doc));
    }

    @Override
    public int batchImport(List<KnowledgePointUpsertRequest> requests) {
        ensureAvailableForWrite();
        ThrowUtils.throwIf(requests == null || requests.isEmpty(), ErrorCode.PARAMS_ERROR, "导入列表不能为空");
        ensureCollection();

        List<KnowledgePointUpsertRequest> valid = new ArrayList<>();
        for (KnowledgePointUpsertRequest req : requests) {
            validateUpsert(req);
            valid.add(req);
        }
        List<String> texts = valid.stream().map(this::buildEmbedText).toList();
        List<List<Float>> vectors = embeddingManager.embedBatch(texts);
        ThrowUtils.throwIf(vectors.size() != valid.size(), ErrorCode.OPERATION_ERROR, "Embedding 数量与知识点不一致");

        List<Map<String, Object>> docs = new ArrayList<>(valid.size());
        for (int i = 0; i < valid.size(); i++) {
            List<Float> vector = vectors.get(i);
            ThrowUtils.throwIf(vector == null || vector.isEmpty(), ErrorCode.OPERATION_ERROR, "Embedding 向量为空");
            docs.add(DashVectorHttpClient.buildDoc(
                    valid.get(i).getId().trim(), vector, toFields(valid.get(i))));
        }

        int success = 0;
        int batchSize = 20;
        String collection = dashVectorProperties.getCollection();
        for (int i = 0; i < docs.size(); i += batchSize) {
            List<Map<String, Object>> batch = docs.subList(i, Math.min(i + batchSize, docs.size()));
            dashVectorHttpClient.upsertDocs(collection, new ArrayList<>(batch));
            success += batch.size();
            log.info("知识点批量写入成功: collection={}, batchEnd={}", collection, success);
        }
        return success;
    }

    @Override
    public int importSample() {
        String json = ResourceUtil.readUtf8Str(SAMPLE_PATH);
        ThrowUtils.throwIf(StrUtil.isBlank(json), ErrorCode.NOT_FOUND_ERROR, "未找到样例文件 " + SAMPLE_PATH);
        JSONArray array = JSONUtil.parseArray(json);
        List<KnowledgePointUpsertRequest> list = JSONUtil.toList(array, KnowledgePointUpsertRequest.class);
        return batchImport(list);
    }

    @Override
    public void delete(String id) {
        ensureAvailableForWrite();
        ThrowUtils.throwIf(StrUtil.isBlank(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        ensureCollection();
        dashVectorHttpClient.deleteDocs(dashVectorProperties.getCollection(), List.of(id.trim()));
    }

    @Override
    public List<KnowledgePointVO> search(String query, Integer topK) {
        ThrowUtils.throwIf(!isAvailable(), ErrorCode.OPERATION_ERROR,
                "向量知识库不可用，请检查 aliyun.dashvector / dashscope 配置");
        ThrowUtils.throwIf(StrUtil.isBlank(query), ErrorCode.PARAMS_ERROR, "query 不能为空");
        String collection = dashVectorProperties.getCollection();
        ThrowUtils.throwIf(!dashVectorHttpClient.collectionExists(collection), ErrorCode.OPERATION_ERROR,
                "Collection 不存在: " + collection + "，请先调用 /knowledge/import/sample");
        int k = topK == null || topK <= 0 ? dashVectorProperties.getTopK() : topK;
        List<Float> vector = embeddingManager.embed(query.trim());
        // 先验证样例 id 是否真在库中（便于定位「写入成功但搜不到」）
        List<JSONObject> probe = dashVectorHttpClient.fetchDocs(collection, List.of("spring_ioc_01"));
        log.info("检索前探活 fetch spring_ioc_01 => {} 条, collection={}", probe.size(), collection);
        List<JSONObject> hits = dashVectorHttpClient.query(collection, vector, k);
        List<KnowledgePointVO> result = new ArrayList<>();
        for (JSONObject hit : hits) {
            result.add(toVo(hit));
        }
        return result;
    }

    private void ensureAvailableForWrite() {
        ThrowUtils.throwIf(!dashVectorProperties.isEnabled(), ErrorCode.OPERATION_ERROR,
                "未启用 DashVector，请设置 aliyun.dashvector.enabled=true");
        ThrowUtils.throwIf(!dashVectorHttpClient.isConfigured(), ErrorCode.OPERATION_ERROR,
                "DashVector 未配置 apiKey/endpoint");
        ThrowUtils.throwIf(!embeddingManager.isConfigured(), ErrorCode.OPERATION_ERROR,
                "未配置 DashScope apiKey，无法向量化");
    }

    private void ensureCollection() {
        String name = dashVectorProperties.getCollection();
        if (dashVectorHttpClient.collectionExists(name)) {
            return;
        }
        synchronized (this) {
            if (dashVectorHttpClient.collectionExists(name)) {
                return;
            }
            log.info("DashVector Collection 不存在，HTTP 创建单向量集合: name={}, dim={}",
                    name, dashVectorProperties.getDimension());
            Map<String, String> schema = new LinkedHashMap<>();
            schema.put("title", "STRING");
            schema.put("content", "STRING");
            schema.put("skill_tags", "STRING");
            schema.put("difficulty", "STRING");
            dashVectorHttpClient.createCollection(name, dashVectorProperties.getDimension(), schema);
        }
    }

    private void validateUpsert(KnowledgePointUpsertRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getId()), ErrorCode.PARAMS_ERROR, "id 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getTitle()), ErrorCode.PARAMS_ERROR, "title 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(request.getContent()), ErrorCode.PARAMS_ERROR, "content 不能为空");
    }

    private String buildEmbedText(KnowledgePointUpsertRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getTitle().trim());
        if (StrUtil.isNotBlank(request.getSkillTags())) {
            sb.append('\n').append(request.getSkillTags().trim());
        }
        sb.append('\n').append(request.getContent().trim());
        return sb.toString();
    }

    private Map<String, Object> toFields(KnowledgePointUpsertRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", request.getTitle().trim());
        fields.put("content", request.getContent().trim());
        fields.put("skill_tags", StrUtil.blankToDefault(request.getSkillTags(), "").trim());
        fields.put("difficulty", StrUtil.blankToDefault(request.getDifficulty(), "").trim());
        return fields;
    }

    private KnowledgePointVO toVo(JSONObject hit) {
        KnowledgePointVO vo = new KnowledgePointVO();
        vo.setId(hit.getStr("id"));
        vo.setScore(hit.getFloat("score"));
        JSONObject fields = hit.getJSONObject("fields");
        if (fields != null) {
            vo.setTitle(fields.getStr("title"));
            vo.setContent(fields.getStr("content"));
            vo.setSkillTags(fields.getStr("skill_tags"));
            vo.setDifficulty(fields.getStr("difficulty"));
        }
        return vo;
    }
}
