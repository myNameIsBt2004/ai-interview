package com.aiinterview.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.config.TtsProperties;
import com.aiinterview.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 火山引擎语音合成（openspeech HTTP V1）
 */
@Service
@Slf4j
public class VolcTtsManager {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Resource
    private TtsProperties ttsProperties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 合成语音，返回 Base64 音频
     */
    public String synthesize(String text) {
        if (!ttsProperties.useVolc()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前未启用火山 TTS（ai.tts.provider=volc）");
        }
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "合成文本不能为空");
        }
        if (StrUtil.hasBlank(ttsProperties.getAppId(), ttsProperties.getAccessToken())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "火山 TTS 未配置 appId / accessToken");
        }

        String cleaned = text.trim();
        // 火山 V1 单次建议不超过约 300 字，过长则截断
        if (cleaned.length() > 300) {
            cleaned = cleaned.substring(0, 300);
        }

        Map<String, Object> body = new HashMap<>();
        Map<String, Object> app = new HashMap<>();
        app.put("appid", ttsProperties.getAppId());
        app.put("token", ttsProperties.getAccessToken());
        app.put("cluster", ttsProperties.getCluster());
        body.put("app", app);

        Map<String, Object> user = new HashMap<>();
        user.put("uid", "ai-interview");
        body.put("user", user);

        Map<String, Object> audio = new HashMap<>();
        audio.put("voice_type", ttsProperties.getVoiceType());
        audio.put("encoding", ttsProperties.getEncoding());
        audio.put("speed_ratio", ttsProperties.getSpeedRatio());
        body.put("audio", audio);

        Map<String, Object> request = new HashMap<>();
        request.put("reqid", UUID.randomUUID().toString());
        request.put("text", cleaned);
        request.put("text_type", "plain");
        request.put("operation", "query");
        body.put("request", request);

        Request httpRequest = new Request.Builder()
                .url(ttsProperties.getApiUrl())
                .addHeader("Authorization", "Bearer;" + ttsProperties.getAccessToken())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(JSONUtil.toJsonStr(body), JSON))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("火山 TTS HTTP 失败 status={} body={}", response.code(), respBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "火山 TTS 调用失败: HTTP " + response.code());
            }
            JSONObject json = JSONUtil.parseObj(respBody);
            Integer code = json.getInt("code");
            // 文档约定成功码一般为 3000
            if (code == null || code != 3000) {
                String message = json.getStr("message", "unknown");
                log.error("火山 TTS 业务失败 code={} message={} body={}", code, message, respBody);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "火山 TTS 合成失败: " + message);
            }
            String data = json.getStr("data");
            if (StrUtil.isBlank(data)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "火山 TTS 未返回音频数据");
            }
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("火山 TTS 请求异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "火山 TTS 网络异常");
        }
    }

    @PreDestroy
    public void destroy() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
