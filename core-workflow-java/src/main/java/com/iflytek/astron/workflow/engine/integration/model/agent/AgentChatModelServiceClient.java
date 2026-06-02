package com.iflytek.astron.workflow.engine.integration.model.agent;

import com.iflytek.astron.workflow.engine.constants.ModelTypeEnum;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service layer for resolving agent model parameters and building Spring AI chat models.
 */
@Slf4j
@Service
public class AgentChatModelServiceClient {

    private static final ModelTypeEnum DEFAULT_MODEL_TYPE = ModelTypeEnum.OPENAI;

    private final AgentChatModelFactory agentChatModelFactory;

    public AgentChatModelServiceClient(AgentChatModelFactory agentChatModelFactory) {
        this.agentChatModelFactory = agentChatModelFactory;
    }

    public ChatModel buildChatModel(Map<String, Object> nodeParam) {
        if (nodeParam == null) {
            throw new IllegalArgumentException("AI agent node parameters cannot be null");
        }

        ModelTypeEnum modelType = resolveModelType(nodeParam);
        log.debug("Building AI agent chat model with type: {}", modelType.getCode());
        return agentChatModelFactory.build(modelType, nodeParam);
    }

    private ModelTypeEnum resolveModelType(Map<String, Object> nodeParam) {
        ModelTypeEnum modelType = parseModelType(getParam(nodeParam, "source"));
        if (modelType != null) {
            return modelType;
        }

        modelType = parseModelType(getParam(nodeParam, "modelType"));
        if (modelType != null) {
            return modelType;
        }

        modelType = parseModelType(getParam(nodeParam, "provider"));
        if (modelType != null) {
            return modelType;
        }

        modelType = parseModelType(getParam(nodeParam, "modelId"));
        if (modelType != null && agentChatModelFactory.hasProvider(modelType)) {
            return modelType;
        }

        return DEFAULT_MODEL_TYPE;
    }

    private ModelTypeEnum parseModelType(String value) {
        if (StringUtils.isBlank(value) || !ModelTypeEnum.contains(value.trim())) {
            return null;
        }
        return ModelTypeEnum.fromCode(value.trim());
    }

    private String getParam(Map<String, Object> nodeParam, String key) {
        Object value = nodeParam.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
