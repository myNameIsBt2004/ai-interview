package com.aiinterview.manager;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用的 AI 调用类
 */
@Service
public class AiManager {

    @Resource
    private ArkService aiService;

    /**
     * 模型 ID 或推理接入点 ID（ep-xxx），在 application.yml 的 ai.model 配置
     */
    @Value("${ai.model:deepseek-v3-250324}")
    private String defaultModel;

    /**
     * 调用 AI 接口，获取响应字符串
     *
     * @param userPrompt
     * @return
     */
    public String doChat(String userPrompt) {
        return doChat("", userPrompt, defaultModel);
    }

    /**
     * 调用 AI 接口，获取响应字符串
     *
     * @param systemPrompt
     * @param userPrompt
     * @return
     */
    public String doChat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, defaultModel);
    }

    /**
     * 调用 AI 接口，获取响应字符串
     *
     * @param systemPrompt
     * @param userPrompt
     * @param model
     * @return
     */
    public String doChat(String systemPrompt, String userPrompt, String model) {
        if (StrUtil.isBlank(model)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI 模型未配置");
        }
        // 构造消息列表
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content(systemPrompt).build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content(userPrompt).build();
        messages.add(systemMessage);
        messages.add(userMessage);
        return doChat(messages, model);
    }

    /**
     * 调用 AI 接口，获取响应字符串（允许传入自定义的消息列表，使用默认模型）
     *
     * @param messages
     * @return
     */
    public String doChat(List<ChatMessage> messages) {
        return doChat(messages, defaultModel);
    }

    /**
     * 调用 AI 接口，获取响应字符串（允许传入自定义的消息列表）
     *
     * @param messages
     * @param model
     * @return
     */
    public String doChat(List<ChatMessage> messages, String model) {
        if (StrUtil.isBlank(model)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI 模型未配置");
        }
        // 构造请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .build();
        // 调用接口发送请求
        List<ChatCompletionChoice> choices = aiService.createChatCompletion(chatCompletionRequest).getChoices();
        if (CollUtil.isNotEmpty(choices)) {
            return (String) choices.get(0).getMessage().getContent();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 调用失败，没有返回结果");
    }
}
