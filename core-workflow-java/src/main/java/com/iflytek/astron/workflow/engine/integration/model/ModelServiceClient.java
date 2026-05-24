package com.iflytek.astron.workflow.engine.integration.model;

import com.iflytek.astron.workflow.engine.integration.model.bo.LlmCallback;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmReqBo;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmResVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 模型服务客户端
 * 提供统一的LLM调用接口，屏蔽底层不同模型提供商的实现细节
 * 
 * @author 二哥编程星球&Java进阶之路（沉默王二&一灰）
 * @version 2.0.0
 */
@Slf4j
@Service
public class ModelServiceClient {

    @Autowired
    private ModelFactory modelFactory;
    
    /**
     * 调用LLM进行对话完成
     *      
     * @param req LLM请求参数，必须包含有效的modelId
     * @param callback 流式响应回调函数
     * @return LLM响应结果，包含内容、思考内容和token使用情况
     * @throws IllegalArgumentException 当modelId为空或找不到对应的模型集成时
     * @throws RuntimeException 当LLM调用失败时
     */
    public LlmResVo chatCompletion(LlmReqBo req, LlmCallback callback) {
        if (req == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (req.getModelId() == null || req.getModelId().trim().isEmpty()) {
            throw new IllegalArgumentException("Model ID cannot be null or empty");
        }
        
        log.debug("Calling LLM with model: {}, nodeId: {}", req.getModelId(), req.getNodeId());
        
        try {
            ModelIntegration integration = modelFactory.getModelIntegration(req.getModelId());
            LlmResVo result = integration.call(req, callback);
            
            log.debug("LLM call completed successfully for model: {}", req.getModelId());
            return result;
        } catch (IllegalArgumentException e) {
            log.error("Model integration not found for modelId: {}", req.getModelId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to call LLM for model: {}", req.getModelId(), e);
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }
}
