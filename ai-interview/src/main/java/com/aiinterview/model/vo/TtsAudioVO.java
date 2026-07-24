package com.aiinterview.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class TtsAudioVO implements Serializable {

    /**
     * Base64 音频数据
     */
    private String audioBase64;

    /**
     * 音频格式：mp3 / wav 等
     */
    private String format;
}
