package com.iflytek.astron.workflow.engine.node.impl.agent;

import com.iflytek.astron.workflow.engine.constants.NodeExecStatusEnum;
import com.iflytek.astron.workflow.engine.constants.NodeTypeEnum;
import com.iflytek.astron.workflow.engine.context.EngineContextHolder;
import com.iflytek.astron.workflow.engine.domain.NodeRunResult;
import com.iflytek.astron.workflow.engine.domain.NodeState;
import com.iflytek.astron.workflow.engine.domain.callbacks.GenerateUsage;
import com.iflytek.astron.workflow.engine.domain.chain.Node;
import com.iflytek.astron.workflow.engine.domain.chain.OutputItem;
import com.iflytek.astron.workflow.engine.integration.model.agent.AgentChatModelServiceClient;
import com.iflytek.astron.workflow.engine.integration.model.agent.AgentRagAdvisorService;
import com.iflytek.astron.workflow.engine.node.AbstractNodeExecutor;
import com.iflytek.astron.workflow.engine.util.VariableTemplateRender;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring AI based autonomous agent node executor.
 */
@Slf4j
@Component
public class AiAgentNodeExecutor extends AbstractNodeExecutor {

    private static final int DEFAULT_MAX_ITERATIONS = 5;

    private static final int DEFAULT_MEMORY_WINDOW_SIZE = 20;

    private static final String DEFAULT_SYSTEM_PROMPT_PATH = "prompts/agent-node-system-prompt.md";

    private static final String DEFAULT_SYSTEM_PROMPT = loadDefaultSystemPrompt();

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    private final ObjectProvider<ChatMemory> chatMemoryProvider;

    private final AgentChatModelServiceClient agentChatModelService;

    private final AgentRagAdvisorService agentRagAdvisorService;

    private final ChatMemory fallbackChatMemory;

    public AiAgentNodeExecutor(
            ObjectProvider<ToolCallbackProvider> toolCallbackProviders,
            ObjectProvider<ChatMemory> chatMemoryProvider,
            AgentChatModelServiceClient agentChatModelService,
            AgentRagAdvisorService agentRagAdvisorService
    ) {
        this.toolCallbackProviders = toolCallbackProviders;
        this.chatMemoryProvider = chatMemoryProvider;
        this.agentChatModelService = agentChatModelService;
        this.agentRagAdvisorService = agentRagAdvisorService;
        this.fallbackChatMemory = MessageWindowChatMemory.builder()
                .maxMessages(DEFAULT_MEMORY_WINDOW_SIZE)
                .build();
    }

    @Override
    public NodeTypeEnum getNodeType() {
        return NodeTypeEnum.AGENT;
    }

    @Override
    protected NodeRunResult executeNode(NodeState nodeState, Map<String, Object> inputs) {
        Node node = nodeState.node();
        Map<String, Object> nodeParam = node.getData().getNodeParam();

        String userPrompt = getPrompt(nodeParam, inputs);
        int maxIterations = getInt(nodeParam, "maxIterations", DEFAULT_MAX_ITERATIONS);
        boolean enableValidation = getBoolean(nodeParam, "enableValidation", true);
        boolean enableRag = agentRagAdvisorService.isRagEnabled(nodeParam);
        boolean ragValidationEnabled = agentRagAdvisorService.isRagValidationEnabled(nodeParam);
        List<ToolCallback> availableToolCallbacks = collectAvailableToolCallbacks(nodeParam);
        String systemPrompt = buildSystemPrompt(nodeParam, inputs, maxIterations, enableValidation,
                enableRag, ragValidationEnabled, availableToolCallbacks);
        RetrievalAugmentationAdvisor ragAdvisor = agentRagAdvisorService.buildAdvisor(nodeParam).orElse(null);

        ChatModel chatModel = agentChatModelService.buildChatModel(nodeParam);
        ChatClient chatClient = buildChatClient(chatModel, ragAdvisor, availableToolCallbacks);
        String conversationId = buildConversationId(node);

        log.info("AI agent node: nodeId={}, model={}, promptLength={}, maxIterations={}, enableValidation={}, enableRag={}, ragAdvisorEnabled={}",
                node.getId(), getParam(nodeParam, "domain"), userPrompt.length(), maxIterations, enableValidation,
                enableRag, ragAdvisor != null);

        ChatResponse chatResponse = chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();


        String content = getContent(chatResponse);
        String reason = getReasoningContent(chatResponse);
        Map<String, Object> outputs = formatOutputs(content, reason, node.getData().getOutputs());
        if (getBoolean(nodeParam, "returnTrace", false)) {
            outputs.put("trace", buildTrace(node, nodeParam, maxIterations, enableValidation,
                    enableRag, ragValidationEnabled, ragAdvisor != null, conversationId, content));
        }

        NodeRunResult result = new NodeRunResult();
        result.setInputs(inputs);
        result.setOutputs(outputs);
        result.setRawOutput(content);
        result.setNodeAnswerContent(content);
        result.setNodeAnswerReasoningContent(reason);
        result.setTokenCost(formatUsage(getUsage(chatResponse)));
        result.setStatus(NodeExecStatusEnum.SUCCESS);
        return result;
    }

    private ChatClient buildChatClient(
            ChatModel chatModel,
            RetrievalAugmentationAdvisor ragAdvisor,
            List<ToolCallback> availableToolCallbacks
    ) {
        ChatClient.Builder builder = ChatClient.builder(chatModel);

        //挂advisor
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(MessageChatMemoryAdvisor.builder(resolveChatMemory()).build());
        if (ragAdvisor != null) {
            advisors.add(ragAdvisor);
        }
        builder.defaultAdvisors(advisors);


        if (!availableToolCallbacks.isEmpty()) {
            builder.defaultToolCallbacks(availableToolCallbacks);
        }
        return builder.build();
    }

    private String buildSystemPrompt(
            Map<String, Object> nodeParam,
            Map<String, Object> inputs,
            int maxIterations,
            boolean enableValidation,
            boolean enableRag,
            boolean ragValidationEnabled,
            List<ToolCallback> availableToolCallbacks
    ) {
        String toolSummary = buildToolSummary(availableToolCallbacks);
        String defaultPrompt = DEFAULT_SYSTEM_PROMPT.formatted(maxIterations, enableValidation, enableRag,
                ragValidationEnabled, toolSummary);
        String customPrompt = getParam(nodeParam, "systemTemplate");
        if (customPrompt == null) {
            return defaultPrompt;
        }

        String renderedCustomPrompt = VariableTemplateRender.render(customPrompt, inputs);
        if (getBoolean(nodeParam, "overrideSystemTemplate", false)
                || "override".equalsIgnoreCase(getParam(nodeParam, "systemTemplateMode"))) {
            return renderedCustomPrompt;
        }
        return defaultPrompt + "\n\n补充系统要求：\n" + renderedCustomPrompt;
    }

    private List<ToolCallback> collectAvailableToolCallbacks(Map<String, Object> nodeParam) {
        Set<String> allowedTools = getStringSet(nodeParam.get("allowedTools"));
        Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();

        toolCallbackProviders.orderedStream().forEach(provider -> {
            for (ToolCallback callback : provider.getToolCallbacks()) {
                ToolDefinition definition = callback.getToolDefinition();
                if (definition == null || StringUtils.isBlank(definition.name())) {
                    continue;
                }
                if (allowedTools.isEmpty() || allowedTools.contains(definition.name())) {
                    callbacksByName.putIfAbsent(definition.name(), callback);
                }
            }
        });

        if (!allowedTools.isEmpty() && callbacksByName.isEmpty()) {
            log.warn("No MCP tools matched allowedTools: {}", allowedTools);
        }
        return new ArrayList<>(callbacksByName.values());
    }

    private String buildToolSummary(List<ToolCallback> availableToolCallbacks) {
        if (CollectionUtils.isEmpty(availableToolCallbacks)) {
            return "- 当前节点没有可用工具。不要尝试调用工具。";
        }

        StringBuilder summary = new StringBuilder();
        for (ToolCallback callback : availableToolCallbacks) {
            ToolDefinition definition = callback.getToolDefinition();
            summary.append("- ")
                    .append(definition.name())
                    .append("：")
                    .append(StringUtils.isBlank(definition.description()) ? "无描述" : definition.description())
                    .append('\n');
        }
        return summary.toString().stripTrailing();
    }

    private String getPrompt(Map<String, Object> nodeParam, Map<String, Object> inputs) {
        String userTemplate = getParam(nodeParam, "template");
        if (userTemplate == null) {
            throw new IllegalArgumentException("Missing 'template' in AI agent node parameters");
        }
        return VariableTemplateRender.render(userTemplate, inputs);
    }

    private Map<String, Object> formatOutputs(String content, String reason, List<OutputItem> outItems) {
        Map<String, Object> outputs = new HashMap<>();
        if (CollectionUtils.isEmpty(outItems)) {
            outputs.put("output", content);
            return outputs;
        }

        OutputItem firstItem = outItems.get(0);
        outputs.put(firstItem.getName(), content);
        outItems.stream()
                .filter(item -> "reason".equalsIgnoreCase(item.getName()))
                .findAny()
                .ifPresent(item -> outputs.put(item.getName(), reason == null ? "" : reason));
        return outputs;
    }

    private Map<String, Object> buildTrace(
            Node node,
            Map<String, Object> nodeParam,
            int maxIterations,
            boolean enableValidation,
            boolean enableRag,
            boolean ragValidationEnabled,
            boolean ragAdvisorEnabled,
            String conversationId,
            String content
    ) {
        Map<String, Object> trace = new HashMap<>();
        trace.put("nodeId", node.getId());
        trace.put("modelId", getParam(nodeParam, "modelId"));
        trace.put("model", getParam(nodeParam, "domain"));
        trace.put("conversationId", conversationId);
        trace.put("maxIterations", maxIterations);
        trace.put("enableValidation", enableValidation);
        trace.put("enableRag", enableRag);
        trace.put("ragTopK", agentRagAdvisorService.getRagTopK(nodeParam));
        trace.put("ragValidationEnabled", ragValidationEnabled);
        trace.put("ragAdvisorEnabled", ragAdvisorEnabled);
        trace.put("allowedTools", getStringSet(nodeParam.get("allowedTools")));
        trace.put("answerLength", content == null ? 0 : content.length());
        return trace;
    }

    private static String loadDefaultSystemPrompt() {
        ClassPathResource resource = new ClassPathResource(DEFAULT_SYSTEM_PROMPT_PATH);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load AI agent system prompt: " + DEFAULT_SYSTEM_PROMPT_PATH, e);
        }
    }

    private String buildConversationId(Node node) {
        EngineContextHolder.EngineContext context = EngineContextHolder.get();
        String chatId = context == null || StringUtils.isBlank(context.getChatId()) ? "default" : context.getChatId();
        return chatId + ":" + node.getId();
    }

    private ChatMemory resolveChatMemory() {
        return chatMemoryProvider.getIfAvailable(() -> fallbackChatMemory);
    }

    private String getContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private String getReasoningContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "";
        }
        Object reasoning = chatResponse.getResult().getOutput().getMetadata().get("reasoningContent");
        return reasoning == null ? "" : String.valueOf(reasoning);
    }

    private Usage getUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return new EmptyUsage();
        }
        return chatResponse.getMetadata().getUsage();
    }

    private GenerateUsage formatUsage(Usage usage) {
        GenerateUsage generateUsage = new GenerateUsage();
        generateUsage.setCompletionTokens(defaultZero(usage.getCompletionTokens()));
        generateUsage.setPromptTokens(defaultZero(usage.getPromptTokens()));
        generateUsage.setTotalTokens(defaultZero(usage.getTotalTokens()));
        return generateUsage;
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String getParam(Map<String, Object> nodeParam, String key) {
        Object value = nodeParam.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int getInt(Map<String, Object> nodeParam, String key, int defaultValue) {
        Object value = nodeParam.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            log.warn("Invalid integer config: {}={}", key, value);
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> nodeParam, String key, boolean defaultValue) {
        Object value = nodeParam.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Set<String> getStringSet(Object value) {
        Set<String> values = new LinkedHashSet<>();
        if (value == null) {
            return values;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                    values.add(String.valueOf(item));
                }
            }
            return values;
        }
        String text = String.valueOf(value);
        if (StringUtils.isBlank(text)) {
            return values;
        }
        for (String item : text.split(",")) {
            if (StringUtils.isNotBlank(item)) {
                values.add(item.trim());
            }
        }
        return values;
    }

}
