package com.iflytek.astron.workflow.engine.integration.model;

import com.iflytek.astron.workflow.engine.integration.model.bo.LlmCallback;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmReqBo;
import com.iflytek.astron.workflow.engine.integration.model.bo.LlmResVo;

/**
 * LLM模型集成接口
 * 用于统一不同LLM提供商的调用方式
 *
 * @author YiHui
 * @date 2025/12/1
 */
public interface ModelIntegration {
    /**
     * 调用LLM进行对话完成
     *
     * @param req LLM请求参数
     * @param callback 流式响应回调
     * @return LLM响应结果，包含token使用量、内容和思考内容
     */
    LlmResVo call(LlmReqBo req, LlmCallback callback);

    ModelTypeEnum getModelType();
}
