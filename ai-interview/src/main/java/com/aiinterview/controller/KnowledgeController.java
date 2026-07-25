package com.aiinterview.controller;

import com.aiinterview.common.BaseResponse;
import com.aiinterview.common.ErrorCode;
import com.aiinterview.common.ResultUtils;
import com.aiinterview.exception.ThrowUtils;
import com.aiinterview.model.dto.knowledge.KnowledgePointUpsertRequest;
import com.aiinterview.model.dto.knowledge.KnowledgeSearchRequest;
import com.aiinterview.model.vo.KnowledgePointVO;
import com.aiinterview.service.KnowledgePointService;
import com.aiinterview.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识点向量库管理（简单入库 / 调试检索）
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgePointService knowledgePointService;

    @Resource
    private UserService userService;

    /**
     * 单条写入或更新
     */
    @PostMapping("/upsert")
    public BaseResponse<Boolean> upsert(@RequestBody KnowledgePointUpsertRequest request,
                                        HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        knowledgePointService.upsert(request);
        return ResultUtils.success(true);
    }

    /**
     * 批量导入（JSON 数组）
     */
    @PostMapping("/import")
    public BaseResponse<Integer> batchImport(@RequestBody List<KnowledgePointUpsertRequest> requests,
                                             HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        int count = knowledgePointService.batchImport(requests);
        return ResultUtils.success(count);
    }

    /**
     * 导入内置样例知识点
     */
    @PostMapping("/import/sample")
    public BaseResponse<Integer> importSample(HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        int count = knowledgePointService.importSample();
        return ResultUtils.success(count);
    }

    /**
     * 按 id 删除
     */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> delete(@PathVariable("id") String id,
                                        HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        knowledgePointService.delete(id);
        return ResultUtils.success(true);
    }

    /**
     * 调试检索
     */
    @PostMapping("/search")
    public BaseResponse<List<KnowledgePointVO>> search(@RequestBody KnowledgeSearchRequest request,
                                                       HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        List<KnowledgePointVO> list = knowledgePointService.search(request.getQuery(), request.getTopK());
        return ResultUtils.success(list);
    }

    /**
     * 当前向量库能力是否可用
     */
    @GetMapping("/status")
    public BaseResponse<Map<String, Object>> status(HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        Map<String, Object> data = new HashMap<>();
        data.put("available", knowledgePointService.isAvailable());
        return ResultUtils.success(data);
    }
}
