package com.iflytek.astron.workflow.engine.integration.model.agent;

import com.iflytek.astron.workflow.engine.constants.ModelTypeEnum;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

/**
 * Builds Spring AI chat models for AI agent nodes.
 */
public interface AgentChatModelProvider {

    /**
     * Provider model type, such as openai, claude or gemini.
     *
     * @return model provider type
     */
    ModelTypeEnum getModelType();

    /**
     * Build a chat model from workflow node parameters.
     *
     * @param nodeParam agent node parameters
     * @return chat model
     */
    ChatModel build(Map<String, Object> nodeParam);
}
