package com.aiinterview.model.dto.tts;

import lombok.Data;

import java.io.Serializable;

@Data
public class TtsSynthesizeRequest implements Serializable {

    /**
     * 待合成文本
     */
    private String text;
}
