package com.iflytek.astron.workflow.engine.integration.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("RAG text ingestion")
class RagTextIngestionServiceTest {

    @Test
    @DisplayName("splits uploaded text and writes chunk metadata")
    void ingestSplitsTextAndWritesMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(12)
                .withMinChunkSizeChars(0)
                .withMinChunkLengthToEmbed(1)
                .withMaxNumChunks(100)
                .build();
        RagTextIngestionService service = new RagTextIngestionService(vectorStore, splitter);

        RagTextIngestionService.RagTextIngestionRequest request = new RagTextIngestionService.RagTextIngestionRequest();
        request.setKnowledgeBaseId("kb-001");
        request.setDatasetId("ds-001");
        request.setDocumentId("doc-001");
        request.setTitle("Test Doc");
        request.setText("alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau");
        request.setMetadata(Map.of("owner", "tester", "nested", Map.of("k", "v")));

        RagTextIngestionService.RagTextIngestionResult result = service.ingest(request);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> documents = captor.getValue();

        assertTrue(documents.size() > 1);
        assertEquals("doc-001", result.documentId());
        assertEquals(documents.size(), result.chunkCount());
        assertEquals("kb-001", documents.get(0).getMetadata().get("knowledgeBaseId"));
        assertEquals("ds-001", documents.get(0).getMetadata().get("datasetId"));
        assertEquals("doc-001", documents.get(0).getMetadata().get("documentId"));
        assertEquals("Test Doc", documents.get(0).getMetadata().get("title"));
        assertEquals(0, documents.get(0).getMetadata().get("chunkIndex"));
        assertEquals(RagTextIngestionService.SOURCE_TYPE_UPLOADED_TEXT, documents.get(0).getMetadata().get("sourceType"));
        assertTrue(documents.get(0).getMetadata().containsKey("createdAt"));
        assertEquals("{k=v}", documents.get(0).getMetadata().get("nested"));
    }

    @Test
    @DisplayName("rejects empty uploaded text")
    void ingestRejectsEmptyText() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagTextIngestionService service = new RagTextIngestionService(vectorStore);
        RagTextIngestionService.RagTextIngestionRequest request = new RagTextIngestionService.RagTextIngestionRequest();
        request.setKnowledgeBaseId("kb-001");
        request.setText(" ");

        assertThrows(IllegalArgumentException.class, () -> service.ingest(request));
    }
}
