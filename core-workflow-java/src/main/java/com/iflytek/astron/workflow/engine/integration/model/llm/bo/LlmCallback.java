package com.iflytek.astron.workflow.engine.integration.model.llm.bo;

import org.springframework.ai.chat.model.ChatResponse;

/**
 * @author YiHui
 * @date 2025/12/1
 */
public interface LlmCallback {

    void onResponse(ChatResponse response);

}
