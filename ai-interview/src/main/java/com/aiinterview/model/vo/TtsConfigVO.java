package com.aiinterview.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class TtsConfigVO implements Serializable {

    /**
     * browser | volc
     */
    private String provider;
}
