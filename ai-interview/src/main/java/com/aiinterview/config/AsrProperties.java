package com.aiinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音识别（ASR）配置。默认复用 ai.tts 的 appId / accessToken。
 */
@Component
@ConfigurationProperties(prefix = "ai.asr")
@Data
public class AsrProperties {

    /**
     * 是否启用后端 ASR。true 时前端优先走录音上传识别。
     */
    private boolean enabled = true;

    /**
     * 火山语音 AppID；为空则回退 ai.tts.appId
     */
    private String appId = "";

    /**
     * 火山语音 Access Token；为空则回退 ai.tts.accessToken
     */
    private String accessToken = "";

    /**
     * 新版控制台 API Key（UUID 形式）；若填写会额外带 X-Api-Key
     */
    private String apiKey = "";

    /**
     * 资源 ID，常见：volc.bigasr.auc / volc.seedasr.auc
     */
    private String resourceId = "volc.bigasr.auc";

    private String submitUrl = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/submit";

    private String queryUrl = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/query";
}
