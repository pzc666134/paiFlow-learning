package com.iflytek.astron.workflow.engine.integration.rag;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ingests uploaded plain text into the configured Spring AI vector store.
 */
@Service
public class RagTextIngestionService {

    public static final String SOURCE_TYPE_UPLOADED_TEXT = "uploaded_text";

    private final VectorStore vectorStore;

    private final TokenTextSplitter textSplitter;

    @Autowired
    public RagTextIngestionService(VectorStore vectorStore) {
        this(vectorStore, TokenTextSplitter.builder().build());
    }

    RagTextIngestionService(VectorStore vectorStore, TokenTextSplitter textSplitter) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
    }

    public RagTextIngestionResult ingest(RagTextIngestionRequest request) {
        validateRequest(request);

        String documentId = StringUtils.hasText(request.getDocumentId())
                ? request.getDocumentId().trim()
                : UUID.randomUUID().toString();
        Map<String, Object> baseMetadata = buildBaseMetadata(request, documentId);
        Document sourceDocument = Document.builder()
                .id(documentId)
                .text(request.getText())
                .metadata(baseMetadata)
                .build();

        List<Document> chunks = textSplitter.split(sourceDocument);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("RAG text produced no embeddable chunks");
        }

        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new LinkedHashMap<>(chunk.getMetadata());
            metadata.put("knowledgeBaseId", baseMetadata.get("knowledgeBaseId"));
            if (baseMetadata.containsKey("datasetId")) {
                metadata.put("datasetId", baseMetadata.get("datasetId"));
            }
            metadata.put("documentId", documentId);
            if (baseMetadata.containsKey("title")) {
                metadata.put("title", baseMetadata.get("title"));
            }
            metadata.put("chunkIndex", i);
            metadata.put("sourceType", SOURCE_TYPE_UPLOADED_TEXT);
            metadata.put("createdAt", LocalDateTime.now());

            documents.add(Document.builder()
                    .id(documentId + "#" + i)
                    .text(chunk.getText())
                    .metadata(metadata)
                    .build());
        }

        vectorStore.add(documents);
        return new RagTextIngestionResult(documentId, documents.size());
    }

    private void validateRequest(RagTextIngestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("RAG text upload request must not be null");
        }
        if (!StringUtils.hasText(request.getKnowledgeBaseId())) {
            throw new IllegalArgumentException("knowledgeBaseId must not be empty");
        }
        if (!StringUtils.hasText(request.getText())) {
            throw new IllegalArgumentException("text must not be empty");
        }
    }

    private Map<String, Object> buildBaseMetadata(RagTextIngestionRequest request, String documentId) {
        Map<String, Object> metadata = sanitizeMetadata(request.getMetadata());
        metadata.put("knowledgeBaseId", request.getKnowledgeBaseId().trim());
        if (StringUtils.hasText(request.getDatasetId())) {
            metadata.put("datasetId", request.getDatasetId().trim());
        }
        metadata.put("documentId", documentId);
        if (StringUtils.hasText(request.getTitle())) {
            metadata.put("title", request.getTitle().trim());
        }
        metadata.put("sourceType", SOURCE_TYPE_UPLOADED_TEXT);
        metadata.put("createdAt", Instant.now().toString());
        return metadata;
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return sanitized;
        }
        metadata.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null) {
                return;
            }
            sanitized.put(key, toMetadataValue(value));
        });
        return sanitized;
    }

    private Object toMetadataValue(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    @Data
    @Schema(description = "RAG text upload request")
    public static class RagTextIngestionRequest {

        @Schema(description = "Knowledge base identifier used for metadata filtering during retrieval", example = "kb-001", requiredMode = Schema.RequiredMode.REQUIRED)
        private String knowledgeBaseId;

        @Schema(description = "Dataset identifier used for optional metadata filtering", example = "ds-001")
        private String datasetId;

        @Schema(description = "Document identifier. A UUID is generated when omitted.", example = "doc-001")
        private String documentId;

        @Schema(description = "Document title", example = "Product FAQ")
        private String title;

        @Schema(description = "Plain text content to split and write to the vector store", example = "This is the RAG document content.", requiredMode = Schema.RequiredMode.REQUIRED)
        private String text;

        @Schema(description = "Additional flat metadata. Values should be scalar types; complex values are converted to strings.")
        private Map<String, Object> metadata;
    }

    @Schema(description = "RAG text upload result")
    public record RagTextIngestionResult(
            @Schema(description = "Document identifier stored in chunk metadata", example = "doc-001")
            String documentId,
            @Schema(description = "Number of chunks written to the vector store", example = "12")
            int chunkCount
    ) {
    }
}
