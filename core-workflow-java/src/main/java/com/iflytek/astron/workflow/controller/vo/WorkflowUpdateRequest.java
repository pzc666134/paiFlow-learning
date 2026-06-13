package com.iflytek.astron.workflow.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * Workflow update request
 */
@Data
@Schema(description = "Workflow update request")
public class WorkflowUpdateRequest {
    @Schema(description = "Workflow name", example = "Customer Service Flow")
    private String name;
    @Schema(description = "Workflow description", example = "Handles customer service questions")
    private String description;
    @Schema(description = "Workflow DSL data")
    private Map<String, Object> data;
    @Schema(description = "Application ID", example = "app-001")
    private String appId;
}
