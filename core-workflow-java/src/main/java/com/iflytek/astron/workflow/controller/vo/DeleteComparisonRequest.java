package com.iflytek.astron.workflow.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Delete comparison request
 */
@Data
@Schema(description = "Workflow comparison delete request")
public class DeleteComparisonRequest {
    @Schema(description = "Workflow ID", example = "184736")
    private String flowId;
    @Schema(description = "Workflow version", example = "V1.0")
    private String version;
}
