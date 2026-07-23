package com.aiinterview.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.constant.CommonConstant;
import com.aiinterview.exception.BusinessException;
import com.aiinterview.exception.ThrowUtils;
import com.aiinterview.manager.AiManager;
import com.aiinterview.mapper.MockInterviewMapper;
import com.aiinterview.model.dto.mockinterview.MockInterviewAddRequest;
import com.aiinterview.model.dto.mockinterview.MockInterviewChatMessage;
import com.aiinterview.model.dto.mockinterview.MockInterviewEventRequest;
import com.aiinterview.model.dto.mockinterview.MockInterviewQueryRequest;
import com.aiinterview.model.entity.MockInterview;
import com.aiinterview.model.entity.User;
import com.aiinterview.model.enums.MockInterviewEventEnum;
import com.aiinterview.model.enums.MockInterviewStatusEnum;
import com.aiinterview.service.MockInterviewService;
import com.aiinterview.utils.SqlUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模拟面试 Service 实现
 */
@Service
public class MockInterviewServiceImpl extends ServiceImpl<MockInterviewMapper, MockInterview>
        implements MockInterviewService {

    @Resource
    private AiManager aiManager;

    @Override
    public Long createMockInterview(MockInterviewAddRequest mockInterviewAddRequest, User loginUser) {
        if (mockInterviewAddRequest == null || loginUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String workExperience = mockInterviewAddRequest.getWorkExperience();
        String jobPosition = mockInterviewAddRequest.getJobPosition();
        String difficulty = mockInterviewAddRequest.getDifficulty();
        ThrowUtils.throwIf(StrUtil.hasBlank(workExperience, jobPosition, difficulty), ErrorCode.PARAMS_ERROR, "参数错误");

        MockInterview mockInterview = new MockInterview();
        mockInterview.setWorkExperience(workExperience);
        mockInterview.setJobPosition(jobPosition);
        mockInterview.setDifficulty(difficulty);
        mockInterview.setUserId(loginUser.getId());
        mockInterview.setStatus(MockInterviewStatusEnum.TO_START.getValue());

        boolean result = this.save(mockInterview);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建失败");
        return mockInterview.getId();
    }

    @Override
    public QueryWrapper<MockInterview> getQueryWrapper(MockInterviewQueryRequest mockInterviewQueryRequest) {
        QueryWrapper<MockInterview> queryWrapper = new QueryWrapper<>();
        if (mockInterviewQueryRequest == null) {
            return queryWrapper;
        }
        Long id = mockInterviewQueryRequest.getId();
        String workExperience = mockInterviewQueryRequest.getWorkExperience();
        String jobPosition = mockInterviewQueryRequest.getJobPosition();
        String difficulty = mockInterviewQueryRequest.getDifficulty();
        Integer status = mockInterviewQueryRequest.getStatus();
        Long userId = mockInterviewQueryRequest.getUserId();
        String sortField = mockInterviewQueryRequest.getSortField();
        String sortOrder = mockInterviewQueryRequest.getSortOrder();

        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.like(StringUtils.isNotBlank(workExperience), "workExperience", workExperience);
        queryWrapper.like(StringUtils.isNotBlank(jobPosition), "jobPosition", jobPosition);
        queryWrapper.like(StringUtils.isNotBlank(difficulty), "difficulty", difficulty);
        queryWrapper.eq(ObjectUtils.isNotEmpty(status), "status", status);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    @Override
    public String handleMockInterviewEvent(MockInterviewEventRequest mockInterviewEventRequest, User loginUser) {
        Long id = mockInterviewEventRequest.getId();
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        MockInterview mockInterview = this.getById(id);
        ThrowUtils.throwIf(mockInterview == null, ErrorCode.PARAMS_ERROR, "模拟面试未创建");
        if (!mockInterview.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        String event = mockInterviewEventRequest.getEvent();
        MockInterviewEventEnum eventEnum = MockInterviewEventEnum.getEnumByValue(event);
        if (eventEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        return switch (eventEnum) {
            case START -> handleChatStartEvent(mockInterview);
            case CHAT -> handleChatMessageEvent(mockInterviewEventRequest, mockInterview);
            case END -> handleChatEndEvent(mockInterview);
        };
    }

    private String handleChatEndEvent(MockInterview mockInterview) {
        String historyMessage = mockInterview.getMessages();
        List<MockInterviewChatMessage> historyMessageList =
                JSONUtil.parseArray(historyMessage).toList(MockInterviewChatMessage.class);
        final List<ChatMessage> chatMessages = transformToChatMessage(historyMessageList);
        String endUserPrompt = "结束";
        final ChatMessage endUserMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(endUserPrompt).build();
        chatMessages.add(endUserMessage);
        String endAnswer = aiManager.doChat(chatMessages);
        ChatMessage endAssistantMessage = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(endAnswer).build();
        chatMessages.add(endAssistantMessage);

        List<MockInterviewChatMessage> mockInterviewChatMessages = transformFromChatMessage(chatMessages);
        String newJsonStr = JSONUtil.toJsonStr(mockInterviewChatMessages);
        MockInterview newUpdateMockInterview = new MockInterview();
        newUpdateMockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        newUpdateMockInterview.setId(mockInterview.getId());
        newUpdateMockInterview.setMessages(newJsonStr);
        boolean newResult = this.updateById(newUpdateMockInterview);
        ThrowUtils.throwIf(!newResult, ErrorCode.SYSTEM_ERROR, "更新失败");
        return endAnswer;
    }

    private String handleChatMessageEvent(MockInterviewEventRequest mockInterviewEventRequest, MockInterview mockInterview) {
        String message = mockInterviewEventRequest.getMessage();
        String historyMessage = mockInterview.getMessages();
        List<MockInterviewChatMessage> historyMessageList =
                JSONUtil.parseArray(historyMessage).toList(MockInterviewChatMessage.class);
        final List<ChatMessage> chatMessages = transformToChatMessage(historyMessageList);
        final ChatMessage chatUserMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(message).build();
        chatMessages.add(chatUserMessage);
        String chatAnswer = aiManager.doChat(chatMessages);
        ChatMessage chatAssistantMessage = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(chatAnswer).build();
        chatMessages.add(chatAssistantMessage);

        List<MockInterviewChatMessage> mockInterviewChatMessages = transformFromChatMessage(chatMessages);
        String newJsonStr = JSONUtil.toJsonStr(mockInterviewChatMessages);
        MockInterview newUpdateMockInterview = new MockInterview();
        newUpdateMockInterview.setId(mockInterview.getId());
        newUpdateMockInterview.setMessages(newJsonStr);
        if (chatAnswer.contains("【面试结束】")) {
            newUpdateMockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        }
        boolean newResult = this.updateById(newUpdateMockInterview);
        ThrowUtils.throwIf(!newResult, ErrorCode.SYSTEM_ERROR, "更新失败");
        return chatAnswer;
    }

    private String handleChatStartEvent(MockInterview mockInterview) {
        String systemPrompt = String.format(
                "你是一位严厉的程序员面试官，我是候选人，来应聘 %s 的 %s 岗位，面试难度为 %s。请你向我依次提出问题（最多 20 个问题），我也会依次回复。在这期间请完全保持真人面试官的口吻，比如适当引导学员、或者表达出你对学员回答的态度。\n"
                        + "必须满足如下要求：\n"
                        + "1. 当学员回复 “开始” 时，你要正式开始面试\n"
                        + "2. 当学员表示希望 “结束面试” 时，你要结束面试\n"
                        + "3. 此外，当你觉得这场面试可以结束时（比如候选人回答结果较差、不满足工作年限的招聘需求、或者候选人态度不礼貌），必须主动提出面试结束，不用继续询问更多问题了。并且要在回复中包含字符串【面试结束】\n"
                        + "4. 面试结束后，应该给出候选人整场面试的表现和总结。",
                mockInterview.getWorkExperience(), mockInterview.getJobPosition(), mockInterview.getDifficulty());
        String userPrompt = "开始";
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        String answer = aiManager.doChat(messages);
        ChatMessage assistantMessage = ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(answer).build();
        messages.add(assistantMessage);

        List<MockInterviewChatMessage> chatMessageList = transformFromChatMessage(messages);
        String jsonStr = JSONUtil.toJsonStr(chatMessageList);
        MockInterview updateMockInterview = new MockInterview();
        updateMockInterview.setStatus(MockInterviewStatusEnum.IN_PROGRESS.getValue());
        updateMockInterview.setId(mockInterview.getId());
        updateMockInterview.setMessages(jsonStr);
        boolean result = this.updateById(updateMockInterview);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "更新失败");
        return answer;
    }

    List<MockInterviewChatMessage> transformFromChatMessage(List<ChatMessage> chatMessageList) {
        return chatMessageList.stream().map(chatMessage -> {
            MockInterviewChatMessage mockInterviewChatMessage = new MockInterviewChatMessage();
            mockInterviewChatMessage.setRole(chatMessage.getRole().value());
            mockInterviewChatMessage.setMessage(chatMessage.getContent().toString());
            return mockInterviewChatMessage;
        }).collect(Collectors.toList());
    }

    List<ChatMessage> transformToChatMessage(List<MockInterviewChatMessage> chatMessageList) {
        return chatMessageList.stream().map(chatMessage ->
                ChatMessage.builder()
                        .role(ChatMessageRole.valueOf(StringUtils.upperCase(chatMessage.getRole())))
                        .content(chatMessage.getMessage())
                        .build()
        ).collect(Collectors.toList());
    }
}
