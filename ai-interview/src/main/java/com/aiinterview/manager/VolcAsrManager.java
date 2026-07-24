package com.aiinterview.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.config.AsrProperties;
import com.aiinterview.config.TtsProperties;
import com.aiinterview.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 火山引擎录音文件识别（短音频 base64 提交 + 轮询）
 */
@Service
@Slf4j
public class VolcAsrManager {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** 常见录音文件识别 resourceId，按优先级尝试（turbo 更快） */
    private static final String[] DEFAULT_RESOURCE_IDS = {
            "volc.bigasr.auc_turbo",
            "volc.bigasr.auc",
            "volc.seedasr.auc",
    };

    @Resource
    private AsrProperties asrProperties;

    @Resource
    private TtsProperties ttsProperties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /** 成功用过的 resourceId，后续优先 */
    private volatile String workingResourceId;

    public boolean isReady() {
        if (!asrProperties.isEnabled()) {
            return false;
        }
        // 有专用 apiKey，或有 appId+token 即可
        if (StrUtil.isNotBlank(asrProperties.getApiKey())) {
            return true;
        }
        return StrUtil.isAllNotBlank(resolveAppId(), resolveToken());
    }

    public String recognize(byte[] audioBytes, String format) {
        if (!asrProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "后端语音识别未启用");
        }
        String appId = resolveAppId();
        String token = resolveToken();
        String apiKey = StrUtil.trim(asrProperties.getApiKey());
        if (StrUtil.isBlank(apiKey) && StrUtil.hasBlank(appId, token)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "未配置语音识别凭证，请填写 ai.asr.apiKey，或 ai.tts.appId / accessToken");
        }
        // 只有 apiKey 时，用其顶一下旧版 Access-Key，避免空 header
        if (StrUtil.isBlank(token) && StrUtil.isNotBlank(apiKey)) {
            token = apiKey;
        }
        if (StrUtil.isBlank(appId) && StrUtil.isNotBlank(apiKey)) {
            appId = apiKey;
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "音频为空");
        }
        if (audioBytes.length > 4 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "录音过长，请分段说话后再转文字");
        }

        String fmt = normalizeFormat(format);
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
        List<String> resourceIds = buildResourceIdCandidates();

        BusinessException lastNotGranted = null;
        for (String resourceId : resourceIds) {
            try {
                String text = recognizeWithResource(appId, token, apiKey, resourceId, audioBase64, fmt);
                workingResourceId = resourceId;
                return text;
            } catch (BusinessException e) {
                String msg = StrUtil.blankToDefault(e.getMessage(), "");
                if (msg.contains("not granted") || msg.contains("resource_id")) {
                    log.warn("ASR resource not granted: {}", resourceId);
                    lastNotGranted = e;
                    continue;
                }
                throw e;
            }
        }

        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "语音识别资源未开通。请打开火山引擎语音控制台 → 应用管理 → 开通「录音文件识别/大模型录音文件识别」，"
                        + "或在 application-local.yml 设置 ai.asr.resourceId 为控制台显示的资源 ID。"
                        + " 详情：" + (lastNotGranted != null ? lastNotGranted.getMessage() : "requested resource not granted"));
    }

    private String recognizeWithResource(String appId, String token, String apiKey, String resourceId,
                                         String audioBase64, String fmt) {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> user = new HashMap<>();
        user.put("uid", "ai-interview");
        body.put("user", user);

        Map<String, Object> audio = new HashMap<>();
        audio.put("data", audioBase64);
        audio.put("format", fmt);
        body.put("audio", audio);

        Map<String, Object> request = new HashMap<>();
        request.put("model_name", "bigmodel");
        request.put("enable_itn", true);
        request.put("enable_punc", true);
        body.put("request", request);

        Request.Builder submitBuilder = new Request.Builder()
                .url(asrProperties.getSubmitUrl())
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Api-App-Key", appId)
                .addHeader("X-Api-Access-Key", token)
                .addHeader("X-Api-Resource-Id", resourceId)
                .addHeader("X-Api-Request-Id", requestId)
                .addHeader("X-Api-Sequence", "-1")
                .post(RequestBody.create(JSONUtil.toJsonStr(body), JSON));
        if (StrUtil.isNotBlank(apiKey)) {
            submitBuilder.addHeader("X-Api-Key", apiKey);
        }
        Request submitReq = submitBuilder.build();

        String logId = "";
        try (Response response = httpClient.newCall(submitReq).execute()) {
            String status = response.header("X-Api-Status-Code", "");
            logId = StrUtil.blankToDefault(response.header("X-Tt-Logid"), "");
            String apiMsg = StrUtil.blankToDefault(response.header("X-Api-Message"), status);
            String respBody = response.body() != null ? response.body().string() : "";
            if (!"20000000".equals(status)) {
                log.warn("ASR submit failed resourceId={} status={} msg={} body={}",
                        resourceId, status, apiMsg, respBody);
                if (isNoSpeechMessage(apiMsg) || isNoSpeechMessage(respBody)) {
                    return "";
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "语音识别提交失败：" + apiMsg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "语音识别提交异常：" + e.getMessage());
        }

        for (int i = 0; i < 50; i++) {
            // 提交后先立刻查一次，再短间隔轮询，降低体感延迟
            if (i > 0) {
                try {
                    Thread.sleep(i < 8 ? 120 : 220);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "识别被中断");
                }
            }
            String text = queryOnce(appId, token, apiKey, resourceId, requestId, logId);
            // null=处理中；非 null（含空串）=已结束
            if (text != null) {
                return text;
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "语音识别超时，请再说一遍或改用打字");
    }

    private String queryOnce(String appId, String token, String apiKey, String resourceId,
                             String requestId, String logId) {
        Request.Builder builder = new Request.Builder()
                .url(asrProperties.getQueryUrl())
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Api-App-Key", appId)
                .addHeader("X-Api-Access-Key", token)
                .addHeader("X-Api-Resource-Id", resourceId)
                .addHeader("X-Api-Request-Id", requestId)
                .post(RequestBody.create("{}", JSON));
        if (StrUtil.isNotBlank(apiKey)) {
            builder.addHeader("X-Api-Key", apiKey);
        }
        if (StrUtil.isNotBlank(logId)) {
            builder.addHeader("X-Tt-Logid", logId);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String status = response.header("X-Api-Status-Code", "");
            String respBody = response.body() != null ? response.body().string() : "{}";
            if ("20000001".equals(status) || "20000002".equals(status)) {
                return null;
            }
            if (!"20000000".equals(status)) {
                String apiMsg = StrUtil.blankToDefault(response.header("X-Api-Message"), status);
                // 静音/无有效语音：视为空结果，不抛错（前端会静默忽略）
                if (isNoSpeechMessage(apiMsg) || isNoSpeechMessage(respBody)) {
                    log.debug("ASR no speech, treat as empty. msg={}", apiMsg);
                    return "";
                }
                log.warn("ASR query failed resourceId={} status={} msg={} body={}",
                        resourceId, status, apiMsg, respBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "语音识别失败：" + apiMsg);
            }
            String text = extractText(respBody);
            return text == null ? "" : text;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "语音识别查询异常：" + e.getMessage());
        }
    }

    private static boolean isNoSpeechMessage(String msg) {
        if (StrUtil.isBlank(msg)) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("no valid speech")
                || m.contains("normal silence")
                || m.contains("silence audio")
                || m.contains("no speech")
                || msg.contains("无有效语音")
                || msg.contains("静音");
    }

    private List<String> buildResourceIdCandidates() {
        Set<String> ids = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(workingResourceId)) {
            ids.add(workingResourceId);
        }
        if (StrUtil.isNotBlank(asrProperties.getResourceId())) {
            ids.add(asrProperties.getResourceId().trim());
        }
        for (String id : DEFAULT_RESOURCE_IDS) {
            ids.add(id);
        }
        return new ArrayList<>(ids);
    }

    private static String normalizeFormat(String format) {
        String fmt = StrUtil.blankToDefault(format, "wav").toLowerCase();
        if (fmt.contains("webm")) {
            return "ogg";
        }
        if (fmt.contains("mpeg") || fmt.contains("mp3")) {
            return "mp3";
        }
        if (fmt.contains("wav")) {
            return "wav";
        }
        if (fmt.contains("ogg")) {
            return "ogg";
        }
        return "wav";
    }

    private static String extractText(String respBody) {
        JSONObject root = JSONUtil.parseObj(respBody);
        JSONObject result = root.getJSONObject("result");
        if (result != null) {
            String text = result.getStr("text");
            if (StrUtil.isNotBlank(text)) {
                return text.trim();
            }
            JSONArray utterances = result.getJSONArray("utterances");
            if (utterances != null && !utterances.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < utterances.size(); i++) {
                    JSONObject u = utterances.getJSONObject(i);
                    if (u != null && StrUtil.isNotBlank(u.getStr("text"))) {
                        sb.append(u.getStr("text"));
                    }
                }
                if (sb.length() > 0) {
                    return sb.toString().trim();
                }
            }
        }
        return StrUtil.blankToDefault(root.getStr("text"), "").trim();
    }

    private String resolveAppId() {
        return StrUtil.blankToDefault(asrProperties.getAppId(), ttsProperties.getAppId());
    }

    private String resolveToken() {
        return StrUtil.blankToDefault(asrProperties.getAccessToken(), ttsProperties.getAccessToken());
    }
}
