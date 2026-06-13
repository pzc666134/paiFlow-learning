package com.iflytek.astron.workflow.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * Save comparison request
 */
@Data
@Schema(description = "Workflow comparison save request")
public class SaveComparisonRequest {
    @Schema(description = "Workflow ID", example = "184736")
    private String flowId;
    @Schema(description = "Comparison data")
    private Map<String, Object> data;
    @Schema(description = "Workflow version", example = "V1.0")
    private String version;
}
