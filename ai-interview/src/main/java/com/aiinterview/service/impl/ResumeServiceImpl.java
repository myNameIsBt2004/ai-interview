package com.aiinterview.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aiinterview.manager.AiManager;
import com.aiinterview.model.vo.ResumeParseVO;
import com.aiinterview.service.ResumeService;
import com.aiinterview.utils.ResumeTextExtractor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 简历解析：提取文本 + AI 结构化字段（项目经验完整保序）
 */
@Service
public class ResumeServiceImpl implements ResumeService {

    private static final String PARSE_SYSTEM_PROMPT = """
            你是简历信息抽取助手。根据用户提供的简历原文，提取结构化信息。
            只输出一个 JSON 对象，不要 markdown 代码块，不要解释。

            字段要求：
            {
              "personalDesc": "个人背景/自我评价，尽量保留原文关键句，可适当整理，不超过800字",
              "yearsOfExperience": "工作年限原文，如：校招应届 / 1年 / 3年；没有则空字符串",
              "coreSkills": "核心技能，尽量完整保留技能关键词，可用逗号或顿号分隔",
              "projects": [
                {
                  "title": "项目名称（必须有，不要编号前缀以外的多余装饰）",
                  "techStack": "技术栈（原文括号或技术列表内容，没有则空字符串）",
                  "timeRange": "项目时间（没有则空字符串）",
                  "details": "该项目的完整描述原文，禁止摘要、禁止删减技术点、禁止合并到其他项目"
                }
              ]
            }

            硬性规则：
            1. projects 必须按简历中出现的先后顺序排列，不得乱序。
            2. 简历里有几个项目就输出几个，不得只保留一个；不得把多个项目合并成一条。
            3. 每个项目必须有独立 title；details 必须完整覆盖该项目原文要点，不要写“等”“省略”。
            4. 不要虚构简历中不存在的项目或技术。
            5. 若找不到明确项目区块，projects 可为 []。""";

    private static final Pattern PROJECT_SECTION = Pattern.compile(
            "(?:个人)?项目经验|项目经历|实习项目|主要项目|Project Experience",
            Pattern.CASE_INSENSITIVE);

    @Resource
    private AiManager aiManager;

    @Override
    public ResumeParseVO parseResume(MultipartFile file) {
        String resumeText = ResumeTextExtractor.extract(file);
        ResumeParseVO vo = new ResumeParseVO();
        vo.setResumeName(file.getOriginalFilename());
        vo.setResumeText(resumeText);

        String rawProjectSection = extractProjectSection(resumeText);

        try {
            String aiJson = aiManager.doChat(PARSE_SYSTEM_PROMPT, "简历原文如下：\n" + resumeText);
            JSONObject obj = JSONUtil.parseObj(extractJsonObject(aiJson));
            vo.setPersonalDesc(StrUtil.blankToDefault(obj.getStr("personalDesc"), "").trim());
            vo.setYearsOfExperience(StrUtil.blankToDefault(obj.getStr("yearsOfExperience"), "").trim());
            vo.setCoreSkills(StrUtil.blankToDefault(obj.getStr("coreSkills"), "").trim());

            String formatted = formatProjects(obj.getJSONArray("projects"));
            // 若 AI 结果明显过短（疑似摘要/漏项），回退用原文项目区块，避免信息缺失
            if (shouldFallbackToRaw(formatted, rawProjectSection, obj.getJSONArray("projects"))) {
                formatted = formatRawProjectSection(rawProjectSection);
            }
            vo.setProjectExperience(formatted);
        } catch (Exception ignored) {
            vo.setProjectExperience(formatRawProjectSection(rawProjectSection));
            if (StrUtil.isBlank(vo.getPersonalDesc())) {
                vo.setPersonalDesc(trimTo(resumeText, 1800));
            }
        }

        if (StrUtil.isBlank(vo.getProjectExperience()) && StrUtil.isNotBlank(rawProjectSection)) {
            vo.setProjectExperience(formatRawProjectSection(rawProjectSection));
        }
        if (StrUtil.isBlank(vo.getPersonalDesc())) {
            vo.setPersonalDesc(trimTo(resumeText, 1800));
        }
        return vo;
    }

    private static boolean shouldFallbackToRaw(String formatted, String rawSection, JSONArray projects) {
        if (StrUtil.isBlank(rawSection)) {
            return false;
        }
        if (StrUtil.isBlank(formatted)) {
            return true;
        }
        int projectCount = projects == null ? 0 : projects.size();
        // 原文里看起来像多个项目标题，但 AI 只吐出 0/1 个
        int rawLikelyCount = countLikelyProjects(rawSection);
        if (rawLikelyCount >= 2 && projectCount < rawLikelyCount) {
            return true;
        }
        // AI 输出短于原文项目区 55% 以上，视为漏内容
        return formatted.length() < rawSection.length() * 0.55;
    }

    private static int countLikelyProjects(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        // 匹配「1. xxx」「### 1.」「项目名(技术栈)」等常见标题形态
        Matcher m1 = Pattern.compile("(?m)^\\s*(?:#{1,3}\\s*)?(?:\\d+[\\.、．)]\\s*|项目\\s*\\d+[:：]?\\s*)").matcher(text);
        int c1 = 0;
        while (m1.find()) {
            c1++;
        }
        Matcher m2 = Pattern.compile("[\\u4e00-\\w]{2,40}\\s*[（(][^）)]{2,80}[）)]\\s*[:：]").matcher(text);
        int c2 = 0;
        while (m2.find()) {
            c2++;
        }
        return Math.max(c1, c2);
    }

    private static String formatProjects(JSONArray projects) {
        if (projects == null || projects.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (int i = 0; i < projects.size(); i++) {
            JSONObject p = projects.getJSONObject(i);
            if (p == null) {
                continue;
            }
            String title = StrUtil.blankToDefault(p.getStr("title"), "未命名项目").trim();
            title = title.replaceFirst("^\\d+[\\.、．)\\s]+", "").trim();
            String tech = StrUtil.blankToDefault(p.getStr("techStack"), "").trim();
            String time = StrUtil.blankToDefault(p.getStr("timeRange"), "").trim();
            String details = StrUtil.blankToDefault(p.getStr("details"), "").trim();
            if (StrUtil.isAllBlank(title, tech, details) || "未命名项目".equals(title) && StrUtil.isBlank(details)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append("### ").append(index++).append(". ").append(title).append('\n');
            if (StrUtil.isNotBlank(time)) {
                sb.append("**时间**: ").append(time).append('\n');
            }
            if (StrUtil.isNotBlank(tech)) {
                sb.append("**技术栈**: ").append(tech).append('\n');
            }
            sb.append("**项目详情**: ").append(StrUtil.isBlank(details) ? "（暂无详情）" : details);
        }
        return sb.toString().trim();
    }

    /**
     * 从简历原文中截取「项目经验」段落，供完整性校验与回退。
     */
    private static String extractProjectSection(String resumeText) {
        if (StrUtil.isBlank(resumeText)) {
            return "";
        }
        Matcher matcher = PROJECT_SECTION.matcher(resumeText);
        if (!matcher.find()) {
            return "";
        }
        int start = matcher.start();
        String rest = resumeText.substring(start);
        // 下一常见大标题处截断
        Pattern nextSection = Pattern.compile(
                "(?m)^\\s*(?:教育经历|教育背景|工作经历|工作经验|实习经历|技能特长|专业技能|自我评价|荣誉奖项|校园经历|兴趣爱好|Education|Work Experience|Skills)\\s*$");
        Matcher next = nextSection.matcher(rest);
        // 跳过当前「项目经验」标题行后再找下一个标题
        int searchFrom = Math.min(rest.length(), matcher.group().length() + 1);
        if (next.find(searchFrom)) {
            return rest.substring(0, next.start()).trim();
        }
        return rest.trim();
    }

    /**
     * 原文回退时尽量按「项目名(技术栈):」拆成带标题的多段，保序不丢字。
     */
    private static String formatRawProjectSection(String section) {
        if (StrUtil.isBlank(section)) {
            return "";
        }
        String body = PROJECT_SECTION.matcher(section).replaceFirst("").trim();
        if (StrUtil.isBlank(body)) {
            body = section.trim();
        }

        List<String[]> parts = splitRawProjects(body);
        if (parts.isEmpty()) {
            return "### 1. 项目经验\n**项目详情**: " + body;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String title = parts.get(i)[0];
            String tech = parts.get(i)[1];
            String details = parts.get(i)[2];
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("### ").append(i + 1).append(". ").append(title).append('\n');
            if (StrUtil.isNotBlank(tech)) {
                sb.append("**技术栈**: ").append(tech).append('\n');
            }
            sb.append("**项目详情**: ").append(details);
        }
        return sb.toString();
    }

    private static List<String[]> splitRawProjects(String body) {
        List<String[]> result = new ArrayList<>();
        // 智能题库平台(Spring Boot + ...): 负责...
        Pattern p = Pattern.compile(
                "([\\u4e00-\\u9fa5A-Za-z0-9_\\-·]{2,40})\\s*[（(]([^）)]{2,120})[）)]\\s*[:：]\\s*");
        Matcher m = p.matcher(body);
        List<int[]> spans = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> techs = new ArrayList<>();
        while (m.find()) {
            spans.add(new int[]{m.start(), m.end()});
            titles.add(m.group(1).trim());
            techs.add(m.group(2).trim());
        }
        if (spans.size() < 1) {
            return result;
        }
        for (int i = 0; i < spans.size(); i++) {
            int detailStart = spans.get(i)[1];
            int detailEnd = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : body.length();
            String details = body.substring(detailStart, detailEnd).trim();
            result.add(new String[]{titles.get(i), techs.get(i), details});
        }
        return result;
    }

    private static String trimTo(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
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
}
