package com.aiinterview.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试事件请求
 */
@Data
public class MockInterviewEventRequest implements Serializable {

    private String event;

    private String message;

    private Long id;

    private static final long serialVersionUID = 1L;
}
