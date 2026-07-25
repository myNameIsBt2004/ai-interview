package com.aiinterview.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
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
import com.aiinterview.model.vo.KnowledgePointVO;
import com.aiinterview.model.vo.MockInterviewReportVO;
import com.aiinterview.service.KnowledgePointService;
import com.aiinterview.service.MockInterviewService;
import com.aiinterview.utils.SqlUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模拟面试 Service 实现
 */
@Slf4j
@Service
public class MockInterviewServiceImpl extends ServiceImpl<MockInterviewMapper, MockInterview>
        implements MockInterviewService {

    private static final String REPORT_SYSTEM_PROMPT = """
            你是资深技术面试评估专家。根据面试对话记录，输出结构化评估报告。
            只输出一个 JSON 对象，不要 markdown，不要解释。字段如下：
            {
              "score": 0到100的整数综合得分,
              "summary": "面试官综合评价，200字以内",
              "observations": ["关键观察1", "关键观察2", "关键观察3"],
              "suggestions": ["核心建议1", "核心建议2", "核心建议3"],
              "abilities": [
                {"name": "表达能力", "score": 0到10整数, "tip": "一句话点评"},
                {"name": "逻辑能力", "score": 0到10整数, "tip": "一句话点评"},
                {"name": "技术深度", "score": 0到10整数, "tip": "一句话点评"},
                {"name": "项目展现力", "score": 0到10整数, "tip": "一句话点评"},
                {"name": "岗位契合度", "score": 0到10整数, "tip": "一句话点评"}
              ],
              "strengths": ["优点1", "优点2", "优点3"],
              "improvements": ["待改进1", "待改进2", "待改进3"],
              "skillMatrix": [
                {
                  "name": "技能维度名",
                  "score": 0到10整数,
                  "evaluation": "评价，结合候选人表述",
                  "advice": "针对性建议",
                  "resources": ["相关学习主题1", "相关学习主题2"]
                }
              ],
              "qaAnalysis": [
                {
                  "question": "面试官问题",
                  "answer": "候选人回答原文或完整摘要，不要过度压缩",
                  "score": 0到10整数,
                  "analysis": "本题简要分析",
                  "followUps": ["可追问点1", "可追问点2"],
                  "comment": "面试官点评，指出优点与不足",
                  "reference": "参考思路，如何更好作答",
                  "referenceAnswer": "参考回答示例，150字以内",
                  "relatedQuestions": ["相关延伸问题1", "相关延伸问题2"]
                }
              ],
              "learningPlan": "学习规划总述，80字以内",
              "learningFocus": [
                {"name": "学习重点名称", "priority": "高或中"}
              ],
              "roadmap": [
                {"stage": "立即行动", "duration": "1-2周", "items": ["行动项1", "行动项2"]},
                {"stage": "短期目标", "duration": "1个月", "items": ["行动项1", "行动项2"]},
                {"stage": "中期规划", "duration": "2-3个月", "items": ["行动项1", "行动项2"]}
              ]
            }
            要求：abilities 固定上述 5 项；skillMatrix 选 3-5 项岗位相关技术；qaAnalysis 覆盖主要问答最多 8 条；roadmap 固定 3 个阶段；learningFocus 3-5 项。""";

    @Resource
    private AiManager aiManager;

    @Resource
    private KnowledgePointService knowledgePointService;

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
        BeanUtils.copyProperties(mockInterviewAddRequest, mockInterview);
        if (StrUtil.isBlank(mockInterview.getInterviewType())) {
            mockInterview.setInterviewType("综合面试");
        }
        if (StrUtil.isBlank(mockInterview.getFocus())) {
            mockInterview.setFocus(mockInterview.getInterviewType());
        }
        if (mockInterview.getDuration() == null || mockInterview.getDuration() <= 0) {
            mockInterview.setDuration(30);
        }
        if (StrUtil.isBlank(mockInterview.getInterviewer())) {
            mockInterview.setInterviewer("程序员坤坤");
        }
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
            case END -> handleChatEndEvent(mockInterviewEventRequest, mockInterview);
        };
    }

    @Override
    public MockInterviewReportVO getReport(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        MockInterview mockInterview = this.getById(id);
        ThrowUtils.throwIf(mockInterview == null, ErrorCode.NOT_FOUND_ERROR);
        if (!mockInterview.getUserId().equals(loginUser.getId()) && !"admin".equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (StrUtil.isBlank(mockInterview.getReportJson())
                && ObjectUtils.equals(mockInterview.getStatus(), MockInterviewStatusEnum.ENDED.getValue())) {
            generateAndSaveReport(mockInterview, null);
            mockInterview = this.getById(id);
        }
        return toReportVO(mockInterview);
    }

    private String handleChatEndEvent(MockInterviewEventRequest request, MockInterview mockInterview) {
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
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            newUpdateMockInterview.setDurationMinutes(request.getDurationMinutes());
        }
        boolean newResult = this.updateById(newUpdateMockInterview);
        ThrowUtils.throwIf(!newResult, ErrorCode.SYSTEM_ERROR, "更新失败");

        mockInterview.setMessages(newJsonStr);
        mockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            mockInterview.setDurationMinutes(request.getDurationMinutes());
        }
        generateAndSaveReport(mockInterview, endAnswer);
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
        boolean ended = chatAnswer.contains("【面试结束】");
        if (ended) {
            newUpdateMockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
            if (mockInterviewEventRequest.getDurationMinutes() != null
                    && mockInterviewEventRequest.getDurationMinutes() > 0) {
                newUpdateMockInterview.setDurationMinutes(mockInterviewEventRequest.getDurationMinutes());
            }
        }
        boolean newResult = this.updateById(newUpdateMockInterview);
        ThrowUtils.throwIf(!newResult, ErrorCode.SYSTEM_ERROR, "更新失败");
        if (ended) {
            mockInterview.setMessages(newJsonStr);
            mockInterview.setStatus(MockInterviewStatusEnum.ENDED.getValue());
            if (mockInterviewEventRequest.getDurationMinutes() != null
                    && mockInterviewEventRequest.getDurationMinutes() > 0) {
                mockInterview.setDurationMinutes(mockInterviewEventRequest.getDurationMinutes());
            }
            generateAndSaveReport(mockInterview, chatAnswer);
        }
        return chatAnswer;
    }

    private String handleChatStartEvent(MockInterview mockInterview) {
        String systemPrompt = buildSystemPrompt(mockInterview);
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
        updateMockInterview.setStartTime(new Date());
        boolean result = this.updateById(updateMockInterview);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "更新失败");
        return answer;
    }

    private String buildSystemPrompt(MockInterview m) {
        StringBuilder sb = new StringBuilder();
        String interviewer = StrUtil.blankToDefault(m.getInterviewer(), "程序员面试官");
        Integer duration = m.getDuration() == null ? 30 : m.getDuration();
        sb.append(String.format(
                "你是一位严厉但专业的程序员面试官，自称「%s」。我是候选人，来应聘 %s 的 %s 岗位，面试难度为 %s，面试类型为 %s，计划时长约 %d 分钟。\n",
                interviewer,
                m.getWorkExperience(),
                m.getJobPosition(),
                m.getDifficulty(),
                StrUtil.blankToDefault(m.getInterviewType(), "综合面试"),
                duration));
        if (StrUtil.isNotBlank(m.getCompanyName())) {
            sb.append("目标公司：").append(m.getCompanyName()).append("。\n");
        }
        if (m.getSalaryMin() != null || m.getSalaryMax() != null) {
            sb.append("期望薪资范围：")
                    .append(m.getSalaryMin() == null ? "?" : m.getSalaryMin())
                    .append("-")
                    .append(m.getSalaryMax() == null ? "?" : m.getSalaryMax())
                    .append("K/月。\n");
        }
        if (StrUtil.isNotBlank(m.getJobDescription())) {
            sb.append("岗位描述：\n").append(m.getJobDescription()).append("\n");
        }
        sb.append("候选人背景：\n");
        if (StrUtil.isNotBlank(m.getPersonalDesc())) {
            sb.append("- 个人描述：").append(m.getPersonalDesc()).append("\n");
        }
        if (StrUtil.isNotBlank(m.getYearsOfExperience())) {
            sb.append("- 个人工作年限：").append(m.getYearsOfExperience()).append("\n");
        }
        if (StrUtil.isNotBlank(m.getCoreSkills())) {
            sb.append("- 核心技能：").append(m.getCoreSkills()).append("\n");
        }
        if (StrUtil.isNotBlank(m.getProjectExperience())) {
            sb.append("- 项目经验：\n").append(m.getProjectExperience()).append("\n");
        }
        if (StrUtil.isNotBlank(m.getResumeText())) {
            String resume = m.getResumeText();
            if (resume.length() > 3000) {
                resume = resume.substring(0, 3000);
            }
            sb.append("- 简历摘录：\n").append(resume).append("\n");
        }
        appendKnowledgePoints(sb, m);
        sb.append("""
                请你向我依次提出问题（最多 20 个问题），并结合岗位描述与候选人背景做针对性追问。在这期间请完全保持真人面试官的口吻。
                必须满足如下要求：
                1. 当学员回复 “开始” 时，你要正式开始面试；开场第一件事必须请候选人做自我介绍（即使系统已提供简历或个人描述，也不可跳过，简历仅作参考，不能代替口头自我介绍）
                2. 当学员表示希望 “结束面试” 时，你要结束面试
                3. 此外，当你觉得这场面试可以结束时（比如候选人回答结果较差、不满足工作年限的招聘需求、或者候选人态度不礼貌），必须主动提出面试结束，不用继续询问更多问题了。并且要在回复中包含字符串【面试结束】
                4. 面试结束后，应该给出候选人整场面试的表现和总结。
                """);
        return sb.toString();
    }

    /**
     * 按岗位与技能栈从向量库召回知识点，注入面试考察范围（失败则静默跳过）
     */
    private void appendKnowledgePoints(StringBuilder sb, MockInterview m) {
        if (knowledgePointService == null || !knowledgePointService.isAvailable()) {
            return;
        }
        try {
            String query = buildKnowledgeQuery(m);
            if (StrUtil.isBlank(query)) {
                return;
            }
            List<KnowledgePointVO> points = knowledgePointService.search(query, null);
            if (points == null || points.isEmpty()) {
                return;
            }
            sb.append("""
                    以下知识点来自知识库，仅作为本轮「优先考察方向」，不是唯一出题范围：
                    - 可围绕它们提问与追问，但勿整段照抄原文
                    - 除优先点外，仍须结合岗位核心能力、核心技能与简历/项目经历做覆盖，保证考察面完整
                    - 不要把整场面试都困在知识库条目上；优先点大约占提问比重的一部分即可，其余留给岗位与候选人背景
                    优先知识点：
                    """);
            int index = 1;
            for (KnowledgePointVO point : points) {
                String title = StrUtil.blankToDefault(point.getTitle(), point.getId());
                String content = StrUtil.blankToDefault(point.getContent(), "");
                if (content.length() > 280) {
                    content = content.substring(0, 280) + "…";
                }
                sb.append(index++).append(". 【").append(title).append("】");
                if (StrUtil.isNotBlank(point.getSkillTags())) {
                    sb.append("（技能：").append(point.getSkillTags()).append("）");
                }
                sb.append(" ").append(content).append("\n");
            }
        } catch (Exception e) {
            log.warn("注入知识点失败，已跳过: {}", e.getMessage());
        }
    }

    private String buildKnowledgeQuery(MockInterview m) {
        StringBuilder q = new StringBuilder();
        if (StrUtil.isNotBlank(m.getJobPosition())) {
            q.append(m.getJobPosition()).append(' ');
        }
        if (StrUtil.isNotBlank(m.getCoreSkills())) {
            q.append(m.getCoreSkills()).append(' ');
        }
        if (StrUtil.isNotBlank(m.getJobDescription())) {
            String jd = m.getJobDescription().trim();
            if (jd.length() > 800) {
                jd = jd.substring(0, 800);
            }
            q.append(jd);
        }
        return q.toString().trim();
    }

    private void generateAndSaveReport(MockInterview mockInterview, String fallbackSummary) {
        String reportJson;
        Integer score;
        try {
            String transcript = buildTranscript(mockInterview.getMessages());
            String userPrompt = String.format("""
                    岗位：%s
                    工作年限要求：%s
                    难度：%s
                    面试官收尾点评（可参考）：%s

                    面试对话记录：
                    %s
                    """,
                    mockInterview.getJobPosition(),
                    mockInterview.getWorkExperience(),
                    mockInterview.getDifficulty(),
                    StrUtil.blankToDefault(fallbackSummary, ""),
                    transcript);
            String aiJson = aiManager.doChat(REPORT_SYSTEM_PROMPT, userPrompt);
            JSONObject obj = JSONUtil.parseObj(extractJsonObject(aiJson));
            score = obj.getInt("score", estimateScore(fallbackSummary));
            obj.set("score", score);
            reportJson = obj.toString();
        } catch (Exception e) {
            score = estimateScore(fallbackSummary);
            JSONObject fallback = new JSONObject();
            fallback.set("score", score);
            fallback.set("summary", StrUtil.blankToDefault(fallbackSummary, "面试已结束，暂无详细评估。"));
            fallback.set("observations", List.of("已完成模拟面试流程", "建议结合岗位 JD 继续补充深度案例"));
            fallback.set("suggestions", List.of("尽量完整作答，避免中途退出", "用 STAR 结构组织项目经历"));
            fallback.set("abilities", defaultAbilities(score));
            fallback.set("strengths", List.of("完成了模拟面试对话"));
            fallback.set("improvements", List.of("可继续强化技术深度与量化结果表述"));
            fallback.set("skillMatrix", new JSONArray());
            fallback.set("qaAnalysis", new JSONArray());
            fallback.set("learningPlan", "建议针对目标岗位补齐核心技术原理与项目亮点表达。");
            fallback.set("learningFocus", List.of(
                    Map.of("name", "面试表达与结构化思维", "priority", "高"),
                    Map.of("name", "岗位核心技术原理", "priority", "高"),
                    Map.of("name", "项目量化与生产实践", "priority", "中")
            ));
            fallback.set("roadmap", List.of(
                    Map.of("stage", "立即行动", "duration", "1-2周",
                            "items", List.of("完成至少 1 次完整模拟面试复盘", "整理 1 个项目的 STAR 表达模板")),
                    Map.of("stage", "短期目标", "duration", "1个月",
                            "items", List.of("系统复习岗位核心八股与原理", "补充项目架构与优化亮点文档")),
                    Map.of("stage", "中期规划", "duration", "2-3个月",
                            "items", List.of("独立完成一个可演示的小系统", "深入一项中间件或框架源码并输出笔记"))
            ));
            reportJson = fallback.toString();
        }

        MockInterview update = new MockInterview();
        update.setId(mockInterview.getId());
        update.setReportJson(reportJson);
        update.setScore(score);
        if (mockInterview.getDurationMinutes() != null) {
            update.setDurationMinutes(mockInterview.getDurationMinutes());
        } else if (mockInterview.getDuration() != null) {
            update.setDurationMinutes(mockInterview.getDuration());
        }
        this.updateById(update);
    }

    private MockInterviewReportVO toReportVO(MockInterview m) {
        MockInterviewReportVO vo = new MockInterviewReportVO();
        vo.setId(m.getId());
        vo.setInterviewType(StrUtil.blankToDefault(m.getInterviewType(), "综合面试"));
        vo.setJobPosition(m.getJobPosition());
        vo.setDifficulty(m.getDifficulty());
        vo.setCompanyName(m.getCompanyName());
        vo.setInterviewer(m.getInterviewer());
        vo.setScore(m.getScore());
        vo.setDurationMinutes(m.getDurationMinutes() != null ? m.getDurationMinutes() : m.getDuration());
        vo.setStartTime(m.getStartTime() != null ? m.getStartTime() : m.getCreateTime());
        vo.setStatus(m.getStatus());

        List<MockInterviewReportVO.ChatItem> chatItems = new ArrayList<>();
        if (StrUtil.isNotBlank(m.getMessages())) {
            List<MockInterviewChatMessage> msgs =
                    JSONUtil.parseArray(m.getMessages()).toList(MockInterviewChatMessage.class);
            for (MockInterviewChatMessage msg : msgs) {
                if ("system".equalsIgnoreCase(msg.getRole())) {
                    continue;
                }
                MockInterviewReportVO.ChatItem item = new MockInterviewReportVO.ChatItem();
                item.setRole(msg.getRole());
                item.setContent(msg.getMessage());
                chatItems.add(item);
            }
        }
        vo.setMessages(chatItems);

        if (StrUtil.isNotBlank(m.getReportJson())) {
            try {
                JSONObject obj = JSONUtil.parseObj(m.getReportJson());
                vo.setScore(obj.getInt("score", m.getScore()));
                vo.setSummary(obj.getStr("summary"));
                vo.setObservations(toStringList(obj.getJSONArray("observations")));
                vo.setSuggestions(toStringList(obj.getJSONArray("suggestions")));
                vo.setStrengths(toStringList(obj.getJSONArray("strengths")));
                vo.setImprovements(toStringList(obj.getJSONArray("improvements")));
                vo.setLearningPlan(obj.getStr("learningPlan"));

                List<MockInterviewReportVO.AbilityItem> abilities = new ArrayList<>();
                JSONArray abilityArr = obj.getJSONArray("abilities");
                if (abilityArr != null) {
                    for (int i = 0; i < abilityArr.size(); i++) {
                        JSONObject a = abilityArr.getJSONObject(i);
                        MockInterviewReportVO.AbilityItem item = new MockInterviewReportVO.AbilityItem();
                        item.setName(a.getStr("name"));
                        item.setScore(a.getInt("score", 6));
                        item.setTip(a.getStr("tip"));
                        abilities.add(item);
                    }
                }
                vo.setAbilities(abilities);

                List<MockInterviewReportVO.SkillItem> skills = new ArrayList<>();
                JSONArray skillArr = obj.getJSONArray("skillMatrix");
                if (skillArr != null) {
                    for (int i = 0; i < skillArr.size(); i++) {
                        JSONObject s = skillArr.getJSONObject(i);
                        MockInterviewReportVO.SkillItem item = new MockInterviewReportVO.SkillItem();
                        item.setName(s.getStr("name"));
                        item.setScore(s.getInt("score", 6));
                        item.setEvaluation(s.getStr("evaluation"));
                        item.setAdvice(s.getStr("advice"));
                        item.setResources(toStringList(s.getJSONArray("resources")));
                        skills.add(item);
                    }
                }
                vo.setSkillMatrix(skills);

                List<MockInterviewReportVO.QaItem> qaList = new ArrayList<>();
                JSONArray qaArr = obj.getJSONArray("qaAnalysis");
                if (qaArr != null) {
                    for (int i = 0; i < qaArr.size(); i++) {
                        JSONObject q = qaArr.getJSONObject(i);
                        MockInterviewReportVO.QaItem item = new MockInterviewReportVO.QaItem();
                        item.setQuestion(q.getStr("question"));
                        item.setAnswer(q.getStr("answer"));
                        item.setScore(q.getInt("score", 6));
                        item.setAnalysis(q.getStr("analysis"));
                        item.setFollowUps(toStringList(q.getJSONArray("followUps")));
                        item.setComment(q.getStr("comment"));
                        item.setReference(q.getStr("reference"));
                        item.setReferenceAnswer(q.getStr("referenceAnswer"));
                        item.setRelatedQuestions(toStringList(q.getJSONArray("relatedQuestions")));
                        qaList.add(item);
                    }
                }
                vo.setQaAnalysis(qaList);
                vo.setQuestionCount(qaList.isEmpty() ? Math.max(1, chatItems.size() / 2) : qaList.size());

                List<MockInterviewReportVO.LearningFocusItem> focusList = new ArrayList<>();
                JSONArray focusArr = obj.getJSONArray("learningFocus");
                if (focusArr != null) {
                    for (int i = 0; i < focusArr.size(); i++) {
                        JSONObject f = focusArr.getJSONObject(i);
                        MockInterviewReportVO.LearningFocusItem item = new MockInterviewReportVO.LearningFocusItem();
                        item.setName(f.getStr("name"));
                        item.setPriority(StrUtil.blankToDefault(f.getStr("priority"), "中"));
                        focusList.add(item);
                    }
                }
                vo.setLearningFocus(focusList);

                List<MockInterviewReportVO.RoadmapItem> roadmapList = new ArrayList<>();
                JSONArray roadmapArr = obj.getJSONArray("roadmap");
                if (roadmapArr != null) {
                    for (int i = 0; i < roadmapArr.size(); i++) {
                        JSONObject r = roadmapArr.getJSONObject(i);
                        MockInterviewReportVO.RoadmapItem item = new MockInterviewReportVO.RoadmapItem();
                        item.setStage(r.getStr("stage"));
                        item.setDuration(r.getStr("duration"));
                        item.setItems(toStringList(r.getJSONArray("items")));
                        roadmapList.add(item);
                    }
                }
                vo.setRoadmap(roadmapList);
            } catch (Exception ignored) {
                vo.setSummary("报告解析失败，请重新生成或查看对话记录");
                vo.setQuestionCount(Math.max(1, chatItems.size() / 2));
            }
        } else {
            vo.setSummary("报告尚未生成");
            vo.setQuestionCount(Math.max(1, chatItems.size() / 2));
        }
        return vo;
    }

    private static List<String> toStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.size(); i++) {
            Object v = arr.get(i);
            if (v != null) {
                list.add(String.valueOf(v));
            }
        }
        return list;
    }

    private static JSONArray defaultAbilities(int score) {
        int base = Math.max(4, Math.min(10, Math.round(score / 10f)));
        JSONArray arr = new JSONArray();
        arr.add(ability("表达能力", Math.min(10, base + 1), "表达较清晰，术语使用较规范"));
        arr.add(ability("逻辑能力", base, "回答有一定结构，可继续强化分层表述"));
        arr.add(ability("技术深度", Math.max(4, base - 1), "建议补充原理与量化指标"));
        arr.add(ability("项目展现力", base, "项目描述完整度中等，可突出个人贡献"));
        arr.add(ability("岗位契合度", Math.min(10, base + 1), "与目标岗位方向基本匹配"));
        return arr;
    }

    private static JSONObject ability(String name, int score, String tip) {
        JSONObject o = new JSONObject();
        o.set("name", name);
        o.set("score", score);
        o.set("tip", tip);
        return o;
    }

    private static String buildTranscript(String messagesJson) {
        if (StrUtil.isBlank(messagesJson)) {
            return "（无对话记录）";
        }
        List<MockInterviewChatMessage> msgs =
                JSONUtil.parseArray(messagesJson).toList(MockInterviewChatMessage.class);
        StringBuilder sb = new StringBuilder();
        for (MockInterviewChatMessage msg : msgs) {
            if ("system".equalsIgnoreCase(msg.getRole())) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(msg.getRole()) ? "面试官" : "候选人";
            sb.append(role).append("：").append(msg.getMessage()).append("\n");
        }
        String text = sb.toString();
        if (text.length() > 10000) {
            return text.substring(text.length() - 10000);
        }
        return text;
    }

    private static String extractJsonObject(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "{}";
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    private static int estimateScore(String text) {
        int len = text == null ? 0 : text.length();
        return Math.min(95, Math.max(55, 60 + len / 80));
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
