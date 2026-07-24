package com.aiinterview.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aiinterview.model.dto.mockinterview.MockInterviewAddRequest;
import com.aiinterview.model.dto.mockinterview.MockInterviewEventRequest;
import com.aiinterview.model.dto.mockinterview.MockInterviewQueryRequest;
import com.aiinterview.model.entity.MockInterview;
import com.aiinterview.model.entity.User;
import com.aiinterview.model.vo.MockInterviewReportVO;

/**
 * 模拟面试 Service
 */
public interface MockInterviewService extends IService<MockInterview> {

    Long createMockInterview(MockInterviewAddRequest mockInterviewAddRequest, User loginUser);

    QueryWrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest mockInterviewQueryRequest);

    String handleMockInterviewEvent(MockInterviewEventRequest mockInterviewEventRequest, User loginUser);

    MockInterviewReportVO getReport(Long id, User loginUser);
}
