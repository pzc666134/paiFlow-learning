package com.iflytek.astron.workflow.engine.integration.model.agent;

import com.iflytek.astron.workflow.engine.constants.ModelTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for Spring AI chat models used by agent nodes.
 */
@Slf4j
@Component
public class AgentChatModelFactory {

    private final Map<ModelTypeEnum, AgentChatModelProvider> providerMap = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private List<AgentChatModelProvider> providers;

    @PostConstruct
    public void init() {
        if (providers == null || providers.isEmpty()) {
            log.warn("No agent chat model providers found during initialization");
            return;
        }

        for (AgentChatModelProvider provider : providers) {
            ModelTypeEnum modelType = provider.getModelType();
            providerMap.put(modelType, provider);
            log.info("Registered agent chat model provider: {} - {}", modelType.getCode(), modelType.getDescription());
        }
        log.info("Total registered agent chat model providers: {}", providerMap.size());
    }

    public ChatModel build(ModelTypeEnum modelType, Map<String, Object> nodeParam) {
        AgentChatModelProvider provider = getProvider(modelType);
        return provider.build(nodeParam);
    }

    public AgentChatModelProvider getProvider(ModelTypeEnum modelType) {
        if (modelType == null) {
            throw new IllegalArgumentException("Model type cannot be null");
        }

        AgentChatModelProvider provider = providerMap.get(modelType);
        if (provider == null) {
            throw new IllegalArgumentException("No agent chat model provider found for type: " + modelType.getCode());
        }
        return provider;
    }

    public boolean hasProvider(ModelTypeEnum modelType) {
        return modelType != null && providerMap.containsKey(modelType);
    }
}
