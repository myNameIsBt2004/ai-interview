package com.aiinterview.controller;

import cn.hutool.core.util.StrUtil;
import com.aiinterview.common.BaseResponse;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.common.ResultUtils;
import com.aiinterview.config.TtsProperties;
import com.aiinterview.exception.BusinessException;
import com.aiinterview.manager.VolcTtsManager;
import com.aiinterview.model.dto.tts.TtsSynthesizeRequest;
import com.aiinterview.model.vo.TtsAudioVO;
import com.aiinterview.model.vo.TtsConfigVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 语音合成接口
 */
@RestController
@RequestMapping("/tts")
@Slf4j
public class TtsController {

    @Resource
    private TtsProperties ttsProperties;

    @Resource
    private VolcTtsManager volcTtsManager;

    /**
     * 获取当前 TTS 策略（browser / volc），供前端决定走浏览器还是调后端
     */
    @GetMapping("/config")
    public BaseResponse<TtsConfigVO> getConfig() {
        TtsConfigVO vo = new TtsConfigVO();
        vo.setProvider(ttsProperties.useVolc() ? "volc" : "browser");
        return ResultUtils.success(vo);
    }

    /**
     * 调用火山引擎合成语音，返回 Base64 音频
     */
    @PostMapping("/synthesize")
    public BaseResponse<TtsAudioVO> synthesize(@RequestBody TtsSynthesizeRequest request) {
        if (request == null || StrUtil.isBlank(request.getText())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "合成文本不能为空");
        }
        if (!ttsProperties.useVolc()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前为浏览器 TTS 模式，无需调用合成接口");
        }
        String audioBase64 = volcTtsManager.synthesize(request.getText());
        TtsAudioVO vo = new TtsAudioVO();
        vo.setAudioBase64(audioBase64);
        vo.setFormat(StrUtil.blankToDefault(ttsProperties.getEncoding(), "mp3"));
        return ResultUtils.success(vo);
    }
}
