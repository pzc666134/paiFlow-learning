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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
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
        boolean ragValidationEnabled = getBoolean(nodeParam, "ragValidationEnabled",
                getBoolean(nodeParam, "enableValidation", true));
        boolean allowEmptyContext = getBoolean(nodeParam, "allowEmptyRagContext", true);


        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .promptTemplate(new PromptTemplate(buildRagPrompt(ragValidationEnabled)))
                .emptyContextPromptTemplate(new PromptTemplate(buildEmptyContextPrompt(ragValidationEnabled)))
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
        return getBoolean(nodeParam, "enableRag", false);
    }

    public int getRagTopK(Map<String, Object> nodeParam) {
        return getInt(nodeParam, "ragTopK", DEFAULT_RAG_TOP_K);
    }

    public boolean isRagValidationEnabled(Map<String, Object> nodeParam) {
        return getBoolean(nodeParam, "ragValidationEnabled", getBoolean(nodeParam, "enableValidation", true));
    }

    private DocumentRetriever resolveDocumentRetriever(Map<String, Object> nodeParam) {
        Optional<DocumentRetriever> documentRetriever = documentRetrieverProvider.orderedStream().findFirst();
        if (documentRetriever.isPresent()) {
            return documentRetriever.get();
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

        // TODO: Add knowledgeBaseId / datasetId / collectionName filter expressions
        // when the project defines a stable metadata schema for vector documents.
        return builder.build();
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

    private String buildRagPrompt(boolean ragValidationEnabled) {
        if (!ragValidationEnabled) {
            return """
                    以下是检索到的上下文，请仅在这些上下文支持的范围内回答用户问题。

                    检索上下文：
                    {context}

                    用户问题：
                    {query}

                    请输出最终答案。
                    """;
        }

        return """
                以下是检索到的上下文，请仅在这些上下文支持的范围内回答用户问题。

                检索上下文：
                {context}

                用户问题：
                {query}

                请完成以下校验：
                1. 上下文是否足以回答用户问题。
                2. 答案中的关键事实是否能被上下文支持。
                3. 是否存在缺失、冲突或不确定的信息。
                4. 如果上下文不足，请明确说明不足之处，不要编造。

                请输出最终答案。
                """;
    }

    private String buildEmptyContextPrompt(boolean ragValidationEnabled) {
        if (!ragValidationEnabled) {
            return """
                    当前没有检索到可用上下文。
                    请基于用户问题谨慎回答；如果缺少依据，请说明无法确认。
                    """;
        }

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

}
