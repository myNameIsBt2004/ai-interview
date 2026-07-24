package com.aiinterview.controller;

import com.aiinterview.common.BaseResponse;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.common.ResultUtils;
import com.aiinterview.exception.ThrowUtils;
import com.aiinterview.model.vo.ResumeParseVO;
import com.aiinterview.service.ResumeService;
import com.aiinterview.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 简历接口
 */
@RestController
@RequestMapping("/resume")
public class ResumeController {

    @Resource
    private ResumeService resumeService;

    @Resource
    private UserService userService;

    /**
     * 上传简历并解析出可编辑字段
     */
    @PostMapping("/parse")
    public BaseResponse<ResumeParseVO> parseResume(@RequestParam("file") MultipartFile file,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "请上传简历文件");
        userService.getLoginUser(request);
        ResumeParseVO vo = resumeService.parseResume(file);
        return ResultUtils.success(vo);
    }
}
