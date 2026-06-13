package com.iflytek.astron.workflow.engine.integration.model.agent;

import io.micrometer.common.util.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds optional RAG advisors for AI agent nodes.
 */
@Service
public class AgentRagAdvisorService {

    private static final int DEFAULT_RAG_TOP_K = 5;

    private static final String DEFAULT_REWRITE_MODEL = "gpt-4o";

    private final ObjectProvider<DocumentRetriever> documentRetrieverProvider;

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    private final ObjectProvider<QueryTransformer> queryTransformerProvider;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    private final AgentChatModelServiceClient agentChatModelService;

    public AgentRagAdvisorService(
            ObjectProvider<DocumentRetriever> documentRetrieverProvider,
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ObjectProvider<QueryTransformer> queryTransformerProvider,
            AgentChatModelServiceClient agentChatModelService
    ) {
        this.documentRetrieverProvider = documentRetrieverProvider;
        this.vectorStoreProvider = vectorStoreProvider;
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.agentChatModelService = agentChatModelService;
        this.queryTransformerProvider = queryTransformerProvider;
    }

    public Optional<RetrievalAugmentationAdvisor> buildAdvisor(Map<String, Object> nodeParam) {
        if (!isRagEnabled(nodeParam)) {
            return Optional.empty();
        }

        DocumentRetriever documentRetriever = resolveDocumentRetriever(nodeParam);
        boolean allowEmptyContext = getBoolean(nodeParam, "allowEmptyRagContext", true);


        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(new PromptTemplate(buildRagPrompt()))
                .emptyContextPromptTemplate(new PromptTemplate(buildEmptyContextPrompt()))
                .allowEmptyContext(allowEmptyContext)
                .build();

        RetrievalAugmentationAdvisor.Builder advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter);

        QueryTransformer transformers = buildQueryTransformers(nodeParam);
        if(transformers != null){
            advisorBuilder.queryTransformers(transformers);
        }
        return Optional.of(advisorBuilder.build());
    }


    public boolean isRagEnabled(Map<String, Object> nodeParam) {
        return getBoolean(nodeParam, "enableValidation", true);
    }

    public int getRagTopK(Map<String, Object> nodeParam) {
        return getInt(nodeParam, "ragTopK", DEFAULT_RAG_TOP_K);
    }

    public boolean isRagValidationEnabled(Map<String, Object> nodeParam) {
        return getBoolean(nodeParam, "ragValidationEnabled", getBoolean(nodeParam, "enableValidation", true));
    }

    DocumentRetriever resolveDocumentRetriever(Map<String, Object> nodeParam) {
        Filter.Expression metadataFilterExpression = buildMetadataFilterExpression(nodeParam);
        if (metadataFilterExpression == null) {
            Optional<DocumentRetriever> documentRetriever = documentRetrieverProvider.orderedStream().findFirst();
            if (documentRetriever.isPresent()) {
                return documentRetriever.get();
            }
        }

        VectorStore vectorStore = vectorStoreProvider.orderedStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "RAG is enabled, but no DocumentRetriever or VectorStore bean is available"));

        VectorStoreDocumentRetriever.Builder builder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(getRagTopK(nodeParam));

        Double similarityThreshold = getDouble(nodeParam, "ragSimilarityThreshold");
        if (similarityThreshold != null) {
            builder.similarityThreshold(similarityThreshold);
        }

        if (metadataFilterExpression != null) {
            builder.filterExpression(metadataFilterExpression);
        }
        return builder.build();
    }

    Filter.Expression buildMetadataFilterExpression(Map<String, Object> nodeParam) {
        String knowledgeBaseId = getParam(nodeParam, "knowledgeBaseId", null);
        String datasetId = getParam(nodeParam, "datasetId", null);
        List<Object> documentIds = getObjectList(nodeParam.get("documentIds"));
        String documentId = getParam(nodeParam, "documentId", null);
        if (StringUtils.isNotBlank(documentId) && !documentIds.contains(documentId)) {
            documentIds.add(documentId);
        }

        if (StringUtils.isBlank(knowledgeBaseId) && StringUtils.isBlank(datasetId) && documentIds.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> expressions = new ArrayList<>();
        if (StringUtils.isNotBlank(knowledgeBaseId)) {
            expressions.add(filterBuilder.eq("knowledgeBaseId", knowledgeBaseId));
        }
        if (StringUtils.isNotBlank(datasetId)) {
            expressions.add(filterBuilder.eq("datasetId", datasetId));
        }
        if (!documentIds.isEmpty()) {
            if (documentIds.size() == 1) {
                expressions.add(filterBuilder.eq("documentId", documentIds.get(0)));
            } else {
                expressions.add(filterBuilder.in("documentId", documentIds));
            }
        }

        FilterExpressionBuilder.Op expression = expressions.get(0);
        for (int i = 1; i < expressions.size(); i++) {
            expression = filterBuilder.and(expression, expressions.get(i));
        }
        return expression.build();
    }

    private QueryTransformer buildQueryTransformers(Map<String, Object> nodeParam) {
        Optional<QueryTransformer> transformer = queryTransformerProvider.orderedStream().findFirst();

        if (getBoolean(nodeParam, "enableRagQueryRewrite", true)) {
            return transformer.orElseGet(() -> RewriteQueryTransformer.builder()
                    .chatClientBuilder(resolveRewriteChatClientBuilder(nodeParam))
                    .targetSearchSystem(getParam(nodeParam, "ragTargetSearchSystem", "vector store"))
                    .build());
        }

        return null;
    }

    private ChatClient.Builder resolveRewriteChatClientBuilder(Map<String, Object> nodeParam) {
        ChatClient.Builder injectedBuilder = chatClientBuilderProvider.getIfAvailable();
        if (injectedBuilder != null) {
            return injectedBuilder;
        }

        Map<String, Object> rewriteModelParam = new HashMap<>(nodeParam);
        rewriteModelParam.put("domain", getParam(nodeParam, "ragRewriteModel", DEFAULT_REWRITE_MODEL));
        ChatModel rewriteChatModel = agentChatModelService.buildChatModel(rewriteModelParam);
        return ChatClient.builder(rewriteChatModel);
    }

    private String buildRagPrompt() {
        return """

            你是一个基于检索上下文回答问题的助手。

            请严格根据【检索上下文】回答【用户问题】，并在回答前进行内部校验：

            1. 上下文是否足以回答用户问题；

            2. 答案中的关键事实是否能被上下文支持；

            3. 上下文中是否存在缺失、冲突或不确定信息；

            4. 如果上下文不足，请明确说明“根据当前上下文无法回答该问题”，不要编造。

            注意：

            - 校验过程只在内部完成，不要输出校验步骤。

            - 最终答案必须完全基于检索上下文。

            - 不要使用上下文以外的知识补充事实。

            - 如果只能部分回答，请说明哪些内容可以回答，哪些内容缺少依据。

            【检索上下文】

            {context}

            【用户问题】

            {query}

            请只输出最终答案。

            """;
    }

    private String buildEmptyContextPrompt() {
        return """
                当前没有检索到可用上下文。
                请明确说明缺少可支持答案的检索依据，不要编造事实。
                """;
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
            return defaultValue;
        }
    }

    private Double getDouble(Map<String, Object> nodeParam, String key) {
        Object value = nodeParam.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
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
        if (StringUtils.isBlank(String.valueOf(value))) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String getParam(Map<String, Object> nodeParam, String key, String defaultValue) {
        Object value = nodeParam.get(key);
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private List<Object> getObjectList(Object value) {
        List<Object> values = new ArrayList<>();
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
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            for (Object item : array) {
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
