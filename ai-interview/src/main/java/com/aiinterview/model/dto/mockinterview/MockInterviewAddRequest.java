package com.aiinterview.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建模拟面试请求
 */
@Data
public class MockInterviewAddRequest implements Serializable {

    private String workExperience;

    private String jobPosition;

    private String difficulty;

    private static final long serialVersionUID = 1L;
}
