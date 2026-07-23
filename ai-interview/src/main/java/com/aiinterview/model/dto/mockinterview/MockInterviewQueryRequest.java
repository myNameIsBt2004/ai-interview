package com.aiinterview.model.dto.mockinterview;

import com.aiinterview.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询模拟面试请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class MockInterviewQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private String workExperience;

    private String jobPosition;

    private String difficulty;

    private Integer status;

    private Long userId;

    private static final long serialVersionUID = 1L;
}
