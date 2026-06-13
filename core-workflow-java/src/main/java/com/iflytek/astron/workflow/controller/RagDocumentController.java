package com.iflytek.astron.workflow.controller;

import com.iflytek.astron.workflow.engine.integration.rag.RagTextIngestionService;
import com.iflytek.astron.workflow.engine.integration.rag.RagTextIngestionService.RagTextIngestionRequest;
import com.iflytek.astron.workflow.engine.integration.rag.RagTextIngestionService.RagTextIngestionResult;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APIs for writing uploaded RAG documents into the vector store.
 */
@Tag(name = "RAG 文本嵌入", description = "上传文本、切片并写入向量库，用于 Agent RAG 检索")
@RestController
@RequestMapping("/api/rag")
public class RagDocumentController {

    private final RagTextIngestionService ragTextIngestionService;

    public RagDocumentController(RagTextIngestionService ragTextIngestionService) {
        this.ragTextIngestionService = ragTextIngestionService;
    }

    @PostMapping(value = "/texts", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "上传 RAG 文本并生成向量嵌入",
            description = """
                    接收纯文本内容，使用 TokenTextSplitter 切片后写入 Spring AI VectorStore。
                    每个切片会携带 knowledgeBaseId、datasetId、documentId、title、chunkIndex、sourceType、createdAt 等 metadata，
                    Agent 节点启用 enableRag=true 后可通过 knowledgeBaseId/datasetId/documentId 进行过滤检索。
                    """
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "待嵌入的 RAG 文本文档",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RagTextIngestionRequest.class),
                    examples = @ExampleObject(
                            name = "RAG 文本嵌入请求",
                            value = """
                                    {
                                      "knowledgeBaseId": "kb-001",
                                      "datasetId": "ds-001",
                                      "documentId": "doc-001",
                                      "title": "产品 FAQ",
                                      "text": "这里是需要写入 RAG 向量库的文本内容。",
                                      "metadata": {
                                        "owner": "admin",
                                        "source": "manual"
                                      }
                                    }
                                    """
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "文本嵌入写入成功",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RagTextIngestionResult.class),
                            examples = @ExampleObject(
                                    name = "文本嵌入响应",
                                    value = """
                                            {
                                              "documentId": "doc-001",
                                              "chunkCount": 12
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "knowledgeBaseId 或 text 为空", content = @Content)
    })
    public RagTextIngestionResult uploadText(@RequestBody RagTextIngestionRequest request) {
        return ragTextIngestionService.ingest(request);
    }
}
