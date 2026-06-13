package com.iflytek.astron.workflow.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * Workflow add request
 */
@Data
@Schema(description = "Workflow create request")
public class WorkflowAddRequest {
    @Schema(description = "Workflow group ID", example = "1")
    private Long groupId;
    @Schema(description = "Workflow name", example = "Customer Service Flow")
    private String name;
    @Schema(description = "Workflow DSL data")
    private Map<String, Object> data;
    @Schema(description = "Workflow description", example = "Handles customer service questions")
    private String description;
    @Schema(description = "Application ID", example = "app-001")
    private String appId;
    @Schema(description = "Workflow source type", example = "1")
    private Integer source;
    @Schema(description = "Workflow version", example = "V1.0")
    private String version;
    @Schema(description = "Workflow tag", example = "0")
    private Integer tag;
}
