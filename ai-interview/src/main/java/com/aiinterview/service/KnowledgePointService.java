package com.aiinterview.service;

import com.aiinterview.model.dto.knowledge.KnowledgePointUpsertRequest;
import com.aiinterview.model.vo.KnowledgePointVO;

import java.util.List;

/**
 * 知识点向量库服务（DashVector）
 */
public interface KnowledgePointService {

    /**
     * 功能是否可用（开关打开且客户端、Embedding 已配置）
     */
    boolean isAvailable();

    /**
     * 单条写入或更新
     */
    void upsert(KnowledgePointUpsertRequest request);

    /**
     * 批量写入或更新
     *
     * @return 成功条数
     */
    int batchImport(List<KnowledgePointUpsertRequest> requests);

    /**
     * 从 classpath 样例文件导入
     */
    int importSample();

    /**
     * 按 id 删除
     */
    void delete(String id);

    /**
     * 语义检索
     */
    List<KnowledgePointVO> search(String query, Integer topK);
}
