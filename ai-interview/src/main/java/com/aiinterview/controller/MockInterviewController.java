package com.aiinterview.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aiinterview.common.BaseResponse;
import com.aiinterview.common.DeleteRequest;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.common.ResultUtils;
import com.aiinterview.exception.BusinessException;
import com.aiinterview.exception.ThrowUtils;
import com.aiinterview.model.dto.mockinterview.MockInterviewAddRequest;
import com.aiinterview.model.dto.mockinterview.MockInterviewEventRequest;
import com.aiinterview.model.dto.mockinterview.MockInterviewQueryRequest;
import com.aiinterview.model.entity.MockInterview;
import com.aiinterview.model.entity.User;
import com.aiinterview.model.vo.MockInterviewReportVO;
import com.aiinterview.service.MockInterviewService;
import com.aiinterview.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 模拟面试接口
 */
@RestController
@RequestMapping("/mockInterview")
@Slf4j
public class MockInterviewController {

    @Resource
    private MockInterviewService mockInterviewService;

    @Resource
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Long> addMockInterview(@RequestBody MockInterviewAddRequest mockInterviewAddRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(mockInterviewAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long mockInterviewId = mockInterviewService.createMockInterview(mockInterviewAddRequest, loginUser);
        return ResultUtils.success(mockInterviewId);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteMockInterview(@RequestBody DeleteRequest deleteRequest,
                                                     HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        MockInterview oldMockInterview = mockInterviewService.getById(id);
        ThrowUtils.throwIf(oldMockInterview == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldMockInterview.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = mockInterviewService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    public BaseResponse<MockInterview> getMockInterviewById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        MockInterview mockInterview = mockInterviewService.getById(id);
        ThrowUtils.throwIf(mockInterview == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(mockInterview);
    }

    @PostMapping("/list/page")
    public BaseResponse<Page<MockInterview>> listMockInterviewByPage(
            @RequestBody MockInterviewQueryRequest mockInterviewQueryRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(mockInterviewQueryRequest == null, ErrorCode.PARAMS_ERROR);
        if (!userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        long current = mockInterviewQueryRequest.getCurrent();
        long pageSize = mockInterviewQueryRequest.getPageSize();
        Page<MockInterview> queryPage = new Page<>(current, pageSize);
        Page<MockInterview> mockInterviewPage = mockInterviewService.page(
                queryPage,
                mockInterviewService.getQueryWrapper(mockInterviewQueryRequest)
        );
        return ResultUtils.success(mockInterviewPage);
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<MockInterview>> listMockInterviewVOByPage(
            @RequestBody MockInterviewQueryRequest mockInterviewQueryRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(mockInterviewQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = mockInterviewQueryRequest.getPageSize();
        long current = mockInterviewQueryRequest.getCurrent();
        long pageSize = mockInterviewQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        mockInterviewQueryRequest.setUserId(loginUser.getId());
        Page<MockInterview> queryPage = new Page<>(current, pageSize);
        Page<MockInterview> mockInterviewPage = mockInterviewService.page(
                queryPage,
                mockInterviewService.getQueryWrapper(mockInterviewQueryRequest)
        );
        return ResultUtils.success(mockInterviewPage);
    }

    @PostMapping("/handleEvent")
    public BaseResponse<String> handleMockInterviewEvent(
            @RequestBody MockInterviewEventRequest mockInterviewEventRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String aiResponse = mockInterviewService.handleMockInterviewEvent(mockInterviewEventRequest, loginUser);
        return ResultUtils.success(aiResponse);
    }

    /**
     * 获取面试评估报告（结束后可查看）
     */
    @GetMapping("/report/get")
    public BaseResponse<MockInterviewReportVO> getMockInterviewReport(long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        MockInterviewReportVO report = mockInterviewService.getReport(id, loginUser);
        return ResultUtils.success(report);
    }
}
