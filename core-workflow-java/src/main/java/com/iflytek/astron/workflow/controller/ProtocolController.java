package com.iflytek.astron.workflow.controller;

import com.iflytek.astron.workflow.controller.vo.DeleteComparisonRequest;
import com.iflytek.astron.workflow.controller.vo.RspVo;
import com.iflytek.astron.workflow.controller.vo.SaveComparisonRequest;
import com.iflytek.astron.workflow.controller.vo.WorkflowAddRequest;
import com.iflytek.astron.workflow.controller.vo.WorkflowUpdateRequest;
import com.iflytek.astron.workflow.engine.domain.WorkflowDSL;
import com.iflytek.astron.workflow.engine.util.AsyncUtil;
import com.iflytek.astron.workflow.engine.util.FlowUtil;
import com.iflytek.astron.workflow.exception.ErrorCode;
import com.iflytek.astron.workflow.flow.entity.WorkflowEntity;
import com.iflytek.astron.workflow.flow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * @author YiHui
 * @date 2025/12/10
 */
@Slf4j
@Tag(name = "Workflow Protocol", description = "Workflow protocol management APIs")
@RestController
@RequestMapping("/workflow/v1/protocol")
public class ProtocolController {

    private final WorkflowService workflowService;

    public ProtocolController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * Add a new workflow
     *
     * @param request Workflow add request
     * @return Response with flow_id
     */
    @Operation(summary = "Create workflow", description = "Create a workflow from protocol data and validate workflow DSL when data is provided.")
    @PostMapping("/add")
    public RspVo addWorkflow(@RequestBody WorkflowAddRequest request) {
        try {
            log.info("Adding workflow: {}", request);

            // Save workflow
            WorkflowEntity savedEntity = workflowService.saveWorkflow(request);

            // Validate workflow if data exists
            if (request.getData() != null && !request.getData().isEmpty() && !"{}".equals(request.getData())) {
                log.info("Starting workflow validation");
                try {
                    WorkflowDSL dsl = workflowService.validateWorkflow(request.getData());
                    log.info("Workflow validation completed");
                } catch (Exception err) {
                    log.error("Workflow validation failed", err);
                    return RspVo.error(ErrorCode.PROTOCOL_VALIDATION_ERROR.getCode(),
                            ErrorCode.PROTOCOL_VALIDATION_ERROR.getMsg(), RspVo.generateSid());
                }
            }

            return RspVo.success(Map.of("flow_id", savedEntity.getId().toString()), RspVo.generateSid());
        } catch (Exception e) {
            log.error("Failed to add workflow", e);
            return RspVo.error(ErrorCode.PROTOCOL_CREATE_ERROR.getCode(),
                    ErrorCode.PROTOCOL_CREATE_ERROR.getMsg(), RspVo.generateSid());
        }
    }

    /**
     * Get workflow by ID
     *
     * @param request Flow read request
     * @return Flow data response
     */
    @Operation(summary = "Get workflow", description = "Get stored workflow metadata and DSL by workflow ID.")
    @PostMapping("/get")
    public RspVo getWorkflow(@RequestBody WorkflowReadRequest request) {
        try {
            WorkflowEntity flow = workflowService.getWorkflow(request.getFlowId());
            return RspVo.success(flow, RspVo.generateSid());
        } catch (Exception e) {
            log.error("Failed to get workflow", e);
            return RspVo.error(ErrorCode.FLOW_GET_ERROR.getCode(),
                    ErrorCode.FLOW_GET_ERROR.getMsg(), RspVo.generateSid());
        }
    }

    /**
     * Update workflow
     *
     * @param flowId  Flow ID to update
     * @param request Flow update data
     * @return Success response
     */
    @Operation(summary = "Update workflow", description = "Update a workflow and validate workflow DSL when data is provided.")
    @PostMapping("/update/{flowId}")
    public RspVo updateWorkflow(@Parameter(description = "Workflow ID", example = "184736") @PathVariable String flowId,
                                @RequestBody WorkflowUpdateRequest request) {
        try {
            log.info("Updating workflow: {}", flowId);

            // Validate workflow if data exists
            if (request.getData() != null && !request.getData().isEmpty()) {
                try {
                    WorkflowDSL dsl = workflowService.validateWorkflow(request.getData());
                    log.info("Workflow validation completed");
                } catch (Exception err) {
                    log.error("Workflow validation failed", err);
                    return RspVo.error(ErrorCode.PROTOCOL_VALIDATION_ERROR.getCode(),
                            ErrorCode.PROTOCOL_VALIDATION_ERROR.getMsg(), RspVo.generateSid());
                }
            }

            workflowService.updateWorkflow(flowId, request);
            return RspVo.success(null, RspVo.generateSid());
        } catch (Exception e) {
            log.error("Failed to update workflow", e);
            return RspVo.error(ErrorCode.PROTOCOL_UPDATE_ERROR.getCode(),
                    ErrorCode.PROTOCOL_UPDATE_ERROR.getMsg(), RspVo.generateSid());
        }
    }

    /**
     * Delete workflow
     *
     * @param request Flow read request
     * @return Success response
     */
    @Operation(summary = "Delete workflow", description = "Delete a workflow by workflow ID.")
    @PostMapping("/delete")
    public RspVo deleteWorkflow(@RequestBody WorkflowReadRequest request) {
        try {
            workflowService.deleteWorkflow(request.getFlowId());
            return RspVo.success(null, RspVo.generateSid());
        } catch (Exception e) {
            log.error("Failed to delete workflow", e);
            return RspVo.error(ErrorCode.PROTOCOL_DELETE_ERROR.getCode(),
                    ErrorCode.PROTOCOL_DELETE_ERROR.getMsg(), RspVo.generateSid());
        }
    }

    /**
     * Build workflow
     *
     * @param flowId Flow ID to build
     * @return Streaming response with build progress
     */
    @Operation(summary = "Build workflow", description = "Build a workflow and stream build progress over SSE.")
    @PostMapping(value = "/build/{flowId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter buildWorkflow(@Parameter(description = "Workflow ID", example = "184736") @PathVariable String flowId) {
        SseEmitter emitter = new SseEmitter(600_000L);

        AsyncUtil.execute(() -> {
            try {
                workflowService.buildWorkflow(flowId);
                emitter.send(SseEmitter.event().name("complete").data(Map.of("end_of_stream", true, "sid", FlowUtil.genSid())));
                emitter.complete();
            } catch (Exception e) {
                log.error("Failed to build workflow", e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("message", "500:" + e.getMessage())));
                } catch (Exception ex) {
                    log.error("Failed to send error to emitter", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Get flow information for MCP input schema
     *
     * @param flowId Flow ID to get info for
     * @return MCP input schema response
     */
    @Operation(summary = "Get workflow MCP input schema", description = "Generate MCP input schema information for a workflow.")
    @GetMapping("/get_flow_info/{flowId}")
    public RspVo getFlowInfo(@Parameter(description = "Consumer username", example = "admin")
                             @RequestHeader("X-Consumer-Username") String consumerUsername,
                             @Parameter(description = "Workflow ID", example = "184736")
                             @PathVariable String flowId) {
        try {
            Map<String, Object> mcpInputSchema = workflowService.generateMcpInputSchema(flowId, consumerUsername);
            return RspVo.success(mcpInputSchema, RspVo.generateSid());
        } catch (Exception e) {
            log.error("Failed to get flow info", e);
            return RspVo.error(ErrorCode.FLOW_GET_ERROR.getCode(),
                    ErrorCode.FLOW_GET_ERROR.getMsg(), RspVo.generateSid());
        }
    }

    /**
     * Save workflow comparison data
     *
     * @param request Comparison data to save
     * @return Success response
     */
    @Operation(summary = "Save workflow comparison", description = "Save workflow comparison data for a specific version.")
    @PostMapping("/compare/save")
    public RspVo saveComparisons(@RequestBody SaveComparisonRequest request) {
        try {
            workflowService.saveComparison(request);
            return RspVo.success(null, RspVo.generateSid());
        } catch (Exception e) {
            log.error("Failed to save comparisons", e);
            return RspVo.error(ErrorCode.PROTOCOL_CREATE_ERROR.getCode(),
                    ErrorCode.PROTOCOL_CREATE_ERROR.getMsg(), RspVo.generateSid());
        }
    }

    /**
     * Delete workflow comparison data
     *
     * @param request Comparison deletion request data
     * @return Success response
     */
    @Operation(summary = "Delete workflow comparison", description = "Delete workflow comparison data for a specific version.")
    @DeleteMapping("/compare/delete")
    public RspVo deleteComparisons(@RequestBody DeleteComparisonRequest request) {
        try {
            workflowService.deleteComparison(request);
            return RspVo.success(null, RspVo.generateSid());
        } catch (Exception e) {
            log.error("Failed to delete comparisons", e);
            return RspVo.error(ErrorCode.PROTOCOL_DELETE_ERROR.getCode(),
                    ErrorCode.PROTOCOL_DELETE_ERROR.getMsg(), RspVo.generateSid());
        }
    }


    /**
     * Workflow read request
     */
    @Data
    @Schema(description = "Workflow read request")
    public static class WorkflowReadRequest {
        @Schema(description = "Workflow ID", example = "184736", requiredMode = Schema.RequiredMode.REQUIRED)
        private String flowId;
        @Schema(description = "Application ID", example = "app-001")
        private String appId;
    }
}
