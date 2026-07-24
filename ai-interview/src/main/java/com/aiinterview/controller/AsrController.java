package com.aiinterview.controller;

import com.aiinterview.common.BaseResponse;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.common.ResultUtils;
import com.aiinterview.exception.BusinessException;
import com.aiinterview.exception.ThrowUtils;
import com.aiinterview.manager.VolcAsrManager;
import com.aiinterview.model.vo.AsrConfigVO;
import com.aiinterview.model.vo.AsrTextVO;
import com.aiinterview.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 语音识别接口
 */
@RestController
@RequestMapping("/asr")
@Slf4j
public class AsrController {

    @Resource
    private VolcAsrManager volcAsrManager;

    @Resource
    private UserService userService;

    @GetMapping("/config")
    public BaseResponse<AsrConfigVO> getConfig() {
        AsrConfigVO vo = new AsrConfigVO();
        vo.setEnabled(volcAsrManager.isReady());
        return ResultUtils.success(vo);
    }

    /**
     * 上传录音并转写为文字
     */
    @PostMapping("/recognize")
    public BaseResponse<AsrTextVO> recognize(@RequestParam("file") MultipartFile file,
                                             HttpServletRequest request) {
        userService.getLoginUser(request);
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "请上传音频");
        if (!volcAsrManager.isReady()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "语音识别未配置，请填写 ai.tts.appId / accessToken 后重启后端");
        }
        try {
            String filename = file.getOriginalFilename() == null ? "audio.wav" : file.getOriginalFilename();
            String format = filename.contains(".")
                    ? filename.substring(filename.lastIndexOf('.') + 1)
                    : "wav";
            String text = volcAsrManager.recognize(file.getBytes(), format);
            AsrTextVO vo = new AsrTextVO();
            vo.setText(text);
            return ResultUtils.success(vo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("ASR failed", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "语音识别失败：" + e.getMessage());
        }
    }
}
