package com.aiinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 语音合成配置。
 * <p>
 * provider=browser：前端使用浏览器 speechSynthesis<br>
 * provider=volc：后端调用火山引擎 openspeech TTS，前端播放返回的音频
 */
@Component
@ConfigurationProperties(prefix = "ai.tts")
@Data
public class TtsProperties {

    /**
     * browser | volc
     */
    private String provider = "browser";

    /**
     * 火山语音应用 AppID（语音控制台，不是方舟 Ark Key）
     */
    private String appId = "";

    /**
     * 火山语音 Access Token
     */
    private String accessToken = "";

    /**
     * 业务集群，默认 volcano_tts
     */
    private String cluster = "volcano_tts";

    /**
     * 音色，如 BV001_streaming（免费）、zh_female_xxx 等
     */
    private String voiceType = "BV001_streaming";

    /**
     * 音频编码：mp3 / wav / pcm
     */
    private String encoding = "mp3";

    /**
     * 语速，默认 1.0
     */
    private double speedRatio = 1.0;

    /**
     * 接口地址
     */
    private String apiUrl = "https://openspeech.bytedance.com/api/v1/tts";

    public boolean useVolc() {
        return "volc".equalsIgnoreCase(provider);
    }
}
