package com.aiinterview.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试消息记录
 */
@Data
public class MockInterviewChatMessage implements Serializable {

    private static final long serialVersionUID = -2056799733159215147L;

    private String role;

    private String message;
}
