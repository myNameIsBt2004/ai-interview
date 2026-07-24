package com.aiinterview.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AsrConfigVO implements Serializable {
    /** 是否可用后端录音识别 */
    private Boolean enabled;
    private static final long serialVersionUID = 1L;
}
