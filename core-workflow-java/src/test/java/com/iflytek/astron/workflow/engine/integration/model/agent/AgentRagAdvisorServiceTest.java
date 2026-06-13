package com.iflytek.astron.workflow.engine.integration.model.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Agent RAG advisor")
class AgentRagAdvisorServiceTest {

    @Test
    @DisplayName("does not build advisor when validation is disabled")
    void buildAdvisorReturnsEmptyWhenRagDisabled() {
        AgentRagAdvisorService service = newService(
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(AgentChatModelServiceClient.class));

        assertTrue(service.buildAdvisor(Map.of("enableValidation", false)).isEmpty());
    }

    @Test
    @DisplayName("uses vector store retriever with metadata filter when knowledge base is configured")
    void resolveRetrieverUsesVectorStoreWhenMetadataFilterExists() {
        ObjectProvider<DocumentRetriever> documentRetrieverProvider = mock(ObjectProvider.class);
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStoreProvider.orderedStream()).thenReturn(Stream.of(vectorStore));
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class))).thenReturn(List.of());

        AgentRagAdvisorService service = newService(
                documentRetrieverProvider,
                vectorStoreProvider,
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(AgentChatModelServiceClient.class));

        DocumentRetriever retriever = service.resolveDocumentRetriever(Map.of(
                "knowledgeBaseId", "kb-001",
                "datasetId", "ds-001",
                "documentIds", List.of("doc-001", "doc-002"),
                "ragTopK", 7,
                "ragSimilarityThreshold", 0.64
        ));
        retriever.retrieve(new Query("hello"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals(7, request.getTopK());
        assertEquals(0.64, request.getSimilarityThreshold());
        assertNotNull(request.getFilterExpression());
        assertTrue(request.getFilterExpression().toString().contains("knowledgeBaseId"));
        assertTrue(request.getFilterExpression().toString().contains("datasetId"));
        assertTrue(request.getFilterExpression().toString().contains("documentId"));
        verify(documentRetrieverProvider, never()).orderedStream();
    }

    @Test
    @DisplayName("keeps injected document retriever when no metadata filter is configured")
    void resolveRetrieverKeepsInjectedRetrieverWithoutMetadataFilter() {
        ObjectProvider<DocumentRetriever> documentRetrieverProvider = mock(ObjectProvider.class);
        ObjectProvider<VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        DocumentRetriever injectedRetriever = query -> List.<Document>of();
        when(documentRetrieverProvider.orderedStream()).thenReturn(Stream.of(injectedRetriever));

        AgentRagAdvisorService service = newService(
                documentRetrieverProvider,
                vectorStoreProvider,
                mock(ObjectProvider.class),
                mock(ObjectProvider.class),
                mock(AgentChatModelServiceClient.class));

        DocumentRetriever retriever = service.resolveDocumentRetriever(Map.of("ragTopK", 3));

        assertEquals(injectedRetriever, retriever);
        verify(vectorStoreProvider, never()).orderedStream();
    }

    private AgentRagAdvisorService newService(
            ObjectProvider<DocumentRetriever> documentRetrieverProvider,
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<?> chatClientBuilderProvider,
            ObjectProvider<QueryTransformer> queryTransformerProvider,
            AgentChatModelServiceClient agentChatModelService
    ) {
        return new AgentRagAdvisorService(
                documentRetrieverProvider,
                vectorStoreProvider,
                (ObjectProvider) chatClientBuilderProvider,
                queryTransformerProvider,
                agentChatModelService);
    }
}
