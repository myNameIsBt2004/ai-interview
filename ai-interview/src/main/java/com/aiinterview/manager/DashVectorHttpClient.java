package com.aiinterview.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.config.AliyunDashVectorProperties;
import com.aiinterview.exception.BusinessException;
import com.aiinterview.exception.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashVector HTTP 客户端（绕过 Java SDK 多向量校验问题）
 */
@Slf4j
@Component
public class DashVectorHttpClient {

    private static final int TIMEOUT_MS = 60_000;

    @Resource
    private AliyunDashVectorProperties properties;

    public boolean isConfigured() {
        return properties.isEnabled()
                && StrUtil.isNotBlank(properties.getApiKey())
                && StrUtil.isNotBlank(properties.getEndpoint());
    }

    public boolean collectionExists(String name) {
        HttpResponse resp = get("/v1/collections/" + name);
        JSONObject body = parseBody(resp);
        boolean ok = body.getInt("code", -1) == 0;
        if (!ok) {
            log.debug("collectionExists({})=false, message={}", name, body.getStr("message"));
        }
        return ok;
    }

    public void createCollection(String name, int dimension, Map<String, String> fieldsSchema) {
        String vectorField = resolveVectorField();
        JSONObject vectorDef = new JSONObject();
        vectorDef.set("dimension", dimension);
        vectorDef.set("metric", "cosine");
        vectorDef.set("dtype", "FLOAT");
        vectorDef.set("data_type", "FLOAT");

        JSONObject vectorsSchema = new JSONObject();
        vectorsSchema.set(vectorField, vectorDef);

        JSONObject req = new JSONObject();
        req.set("name", name);
        // 多向量命名字段，避免落入 proxima_vector
        req.set("vectors_schema", vectorsSchema);
        req.set("vectors", vectorsSchema);
        if (fieldsSchema != null && !fieldsSchema.isEmpty()) {
            req.set("fields_schema", fieldsSchema);
        }
        HttpResponse resp = post("/v1/collections", req.toString());
        JSONObject body = parseBody(resp);
        int code = body.getInt("code", -1);
        String message = StrUtil.blankToDefault(body.getStr("message"), "");
        if (code == 0 || message.toLowerCase().contains("already") || message.contains("已存在")) {
            log.info("Collection 就绪: {}, vectorField={}, code={}, message={}",
                    name, vectorField, code, message);
            return;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "创建 Collection 失败: " + message);
    }

    public void upsertDocs(String collection, List<Map<String, Object>> docs) {
        JSONArray docArr = new JSONArray();
        for (Map<String, Object> doc : docs) {
            docArr.add(toDocJson(doc));
        }
        JSONObject req = new JSONObject();
        req.set("docs", docArr);
        String payload = req.toString();
        log.info("DashVector upsert: collection={}, docs={}, vectorDim={}",
                collection, docArr.size(), firstVectorDim(docs));
        HttpResponse resp = post("/v1/collections/" + collection + "/docs/upsert", payload);
        JSONObject body = parseBody(resp);
        if (body.getInt("code", -1) != 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "DashVector upsert 失败: " + body.getStr("message") + " | raw=" + resp.body());
        }
        // 部分失败会 code=0 但 output 里有错误
        JSONArray output = body.getJSONArray("output");
        if (output != null) {
            for (int i = 0; i < output.size(); i++) {
                JSONObject op = output.getJSONObject(i);
                if (op != null && op.getInt("code", 0) != 0) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR,
                            "DashVector upsert 部分失败: id=" + op.getStr("id")
                                    + ", message=" + op.getStr("message"));
                }
            }
        }
    }

    public void deleteDocs(String collection, List<String> ids) {
        JSONObject req = new JSONObject();
        req.set("ids", ids);
        HttpResponse resp = HttpRequest.delete(baseUrl() + "/v1/collections/" + collection + "/docs")
                .header("dashvector-auth-token", properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .body(req.toString())
                .timeout(TIMEOUT_MS)
                .execute();
        JSONObject body = parseBody(resp);
        if (body.getInt("code", -1) != 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "DashVector 删除失败: " + body.getStr("message"));
        }
    }

    /**
     * 按 id 拉取文档，用于验证是否真正入库
     */
    public List<JSONObject> fetchDocs(String collection, List<String> ids) {
        JSONObject req = new JSONObject();
        req.set("ids", ids);
        HttpResponse resp = post("/v1/collections/" + collection + "/docs/fetch", req.toString());
        // 兼容部分版本用 GET query；若 404 再试 POST docs/get
        if (resp.getStatus() == 404) {
            resp = post("/v1/collections/" + collection + "/query",
                    new JSONObject().set("id", ids.get(0)).set("topk", 1).toString());
        }
        JSONObject body = parseBody(resp);
        if (body.getInt("code", -1) != 0) {
            log.warn("fetchDocs 失败: {}", body.getStr("message"));
            return List.of();
        }
        JSONArray output = body.getJSONArray("output");
        if (output == null || output.isEmpty()) {
            return List.of();
        }
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < output.size(); i++) {
            JSONObject item = output.getJSONObject(i);
            if (item != null) {
                list.add(item);
            }
        }
        return list;
    }

    public List<JSONObject> query(String collection, List<Float> vector, int topK) {
        String vectorField = resolveVectorField();
        // 多向量 Collection：必须用 vectors.{field}.vector，不能用顶层 vector（会映射成 proxima_vector）
        JSONObject vectorQuery = new JSONObject();
        vectorQuery.set("vector", toDoubleArray(vector));
        JSONObject vectors = new JSONObject();
        vectors.set(vectorField, vectorQuery);

        JSONObject req = new JSONObject();
        req.set("vectors", vectors);
        req.set("topk", topK);
        req.set("include_vector", false);
        String payload = req.toString();
        log.info("DashVector query: collection={}, field={}, topk={}, vectorDim={}",
                collection, vectorField, topK, vector == null ? 0 : vector.size());
        HttpResponse resp = post("/v1/collections/" + collection + "/query", payload);
        JSONObject body = parseBody(resp);
        if (body.getInt("code", -1) != 0) {
            log.warn("DashVector query 失败: {} | raw={}", body.getStr("message"), resp.body());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "DashVector 检索失败: " + body.getStr("message"));
        }
        JSONArray output = body.getJSONArray("output");
        if (output == null || output.isEmpty()) {
            log.warn("DashVector query 无结果: collection={}, raw={}", collection, resp.body());
            return List.of();
        }
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < output.size(); i++) {
            list.add(output.getJSONObject(i));
        }
        log.info("DashVector query 命中 {} 条", list.size());
        return list;
    }

    public static Map<String, Object> buildDoc(String id, List<Float> vector, Map<String, Object> fields) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", id);
        doc.put("vector", vector);
        if (fields != null && !fields.isEmpty()) {
            doc.put("fields", fields);
        }
        return doc;
    }

    private JSONObject toDocJson(Map<String, Object> doc) {
        JSONObject json = new JSONObject();
        json.set("id", doc.get("id"));
        Object vector = doc.get("vector");
        if (vector instanceof List<?> list) {
            JSONArray arr = toDoubleArrayFromUnknown(list);
            String vectorField = resolveVectorField();
            // 多向量写入：vectors.embedding = [...]
            JSONObject vectors = new JSONObject();
            vectors.set(vectorField, arr);
            json.set("vectors", vectors);
        }
        Object fields = doc.get("fields");
        if (fields instanceof Map<?, ?> map) {
            json.set("fields", map);
        }
        return json;
    }

    private String resolveVectorField() {
        String field = StrUtil.blankToDefault(properties.getVectorField(), "embedding").trim();
        if ("proxima_vector".equals(field) || "proxima_sparse_vector".equals(field)) {
            return "embedding";
        }
        return field;
    }

    private JSONArray toDoubleArray(List<Float> vector) {
        JSONArray arr = new JSONArray();
        if (vector == null) {
            return arr;
        }
        for (Float f : vector) {
            arr.add(f == null ? 0.0d : f.doubleValue());
        }
        return arr;
    }

    private JSONArray toDoubleArrayFromUnknown(List<?> list) {
        JSONArray arr = new JSONArray();
        for (Object o : list) {
            if (o instanceof Number n) {
                arr.add(n.doubleValue());
            } else {
                arr.add(0.0d);
            }
        }
        return arr;
    }

    private int firstVectorDim(List<Map<String, Object>> docs) {
        if (docs == null || docs.isEmpty()) {
            return 0;
        }
        Object v = docs.get(0).get("vector");
        if (v instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private String baseUrl() {
        String endpoint = properties.getEndpoint().trim();
        if (endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
            return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        }
        return "https://" + endpoint;
    }

    private HttpResponse get(String path) {
        return HttpRequest.get(baseUrl() + path)
                .header("dashvector-auth-token", properties.getApiKey().trim())
                .timeout(TIMEOUT_MS)
                .execute();
    }

    private HttpResponse post(String path, String jsonBody) {
        return HttpRequest.post(baseUrl() + path)
                .header("dashvector-auth-token", properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .timeout(TIMEOUT_MS)
                .execute();
    }

    private JSONObject parseBody(HttpResponse resp) {
        ThrowUtils.throwIf(resp == null, ErrorCode.OPERATION_ERROR, "DashVector 无响应");
        String body = resp.body();
        if (StrUtil.isBlank(body)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "DashVector 空响应, httpStatus=" + resp.getStatus());
        }
        try {
            return JSONUtil.parseObj(body);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "DashVector 响应非 JSON(http=" + resp.getStatus() + "): " + body);
        }
    }
}
