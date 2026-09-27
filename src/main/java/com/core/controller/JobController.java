package com.core.controller;

import com.common.dto.ApiResponse;
import com.core.dto.CandidateOrderResponse;
import com.core.dto.JobRequest;
import com.core.dto.JobResponse;
import com.core.dto.RoutePreviewRequest;
import com.core.dto.RoutePreviewResponse;
import com.core.service.JobService;
import com.core.service.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/endpoint-core-service/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Job", description = "Delivery job route planning APIs")
public class JobController {

    private final JobService jobService;
    private final SecurityService securityService;

    @Operation(summary = "List jobs", description = "Lists active delivery jobs, newest first, each with its ordered delivery stops")
    @GetMapping
    public ResponseEntity<ApiResponse<List<JobResponse>>> listJobs() {
        List<JobResponse> data = jobService.listJobs();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @Operation(summary = "Get candidate orders for a job",
            description = "Lists pending orders that can be added as delivery stops. Without toSubdistrictCode, ranked "
                    + "nearest-first by distance from the given reference point (the job's origin, or the last stop "
                    + "already selected) — candidates restricted to the reference point's province. With "
                    + "toSubdistrictCode (destination-specified mode), ranked by detour "
                    + "(distance(from->order) + distance(order->to) - distance(from->to)) so orders roughly \"on the "
                    + "way\" from origin to destination sort first — candidates restricted to the union of the "
                    + "origin's and destination's provinces. Either ranking sorts unknown/missing distance data last "
                    + "rather than excluding it.")
    @GetMapping("/candidate-orders")
    public ResponseEntity<ApiResponse<List<CandidateOrderResponse>>> getCandidateOrders(
            @RequestParam String fromSubdistrictCode,
            @RequestParam(required = false) String toSubdistrictCode,
            @RequestParam(required = false) String districtCode,
            @RequestParam(required = false) String subdistrictCode) {
        List<CandidateOrderResponse> data = jobService.getCandidateOrders(
                fromSubdistrictCode, toSubdistrictCode, districtCode, subdistrictCode);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @Operation(summary = "Preview a job route",
            description = "Read-only simulation of the route in the given stop order: origin -> stop 1 -> stop 2 -> ... "
                    + "-> optional destination, with the distance of every leg (Google road distance when available, "
                    + "straight-line estimate otherwise). Positions are subdistrict centres. Nothing is saved.")
    @PostMapping("/route-preview")
    public ResponseEntity<ApiResponse<RoutePreviewResponse>> previewRoute(@RequestBody RoutePreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(jobService.previewRoute(request)));
    }

    @Operation(summary = "Create a new job", description = "Creates a delivery job with the selected orders as ordered stops")
    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestBody JobRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        JobResponse data = jobService.createJob(request, username);
        ApiResponse<JobResponse> response = ApiResponse.created(data, "Job created successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update a job", description = "Updates a job's origin, name, schedule, and its ordered list of delivery stops")
    @PutMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId,
            @RequestBody JobRequest request) {
        String username = securityService.extractUsernameFromToken(authHeader);
        JobResponse data = jobService.updateJob(jobId, request, username);
        return ResponseEntity.ok(ApiResponse.success(data, "Job updated successfully"));
    }

    @Operation(summary = "Delete a job", description = "Removes a job and returns its non-completed orders to pending")
    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        jobService.deleteJob(jobId, username);
        return ResponseEntity.ok(ApiResponse.success(null, "Job deleted successfully"));
    }

    @Operation(summary = "Mark a delivery stop as completed",
            description = "Marks the given order's stop within the job as delivered. If every stop in the job is now "
                    + "completed, the job itself is marked completed too.")
    @PatchMapping("/{jobId}/stops/{orderId}/complete")
    public ResponseEntity<ApiResponse<JobResponse>> completeStop(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId,
            @PathVariable Long orderId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        JobResponse data = jobService.completeStop(jobId, orderId, username);
        return ResponseEntity.ok(ApiResponse.success(data, "Stop marked as completed"));
    }

    @Operation(summary = "Skip a delivery stop",
            description = "Marks the given order's stop within the job as skipped. A skipped stop can still be completed "
                    + "while the job is open. Once every stop is completed or skipped, the job is marked completed and "
                    + "skipped orders are returned to PENDING so they can be picked into a new job.")
    @PatchMapping("/{jobId}/stops/{orderId}/skip")
    public ResponseEntity<ApiResponse<JobResponse>> skipStop(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId,
            @PathVariable Long orderId) {
        String username = securityService.extractUsernameFromToken(authHeader);
        JobResponse data = jobService.skipStop(jobId, orderId, username);
        return ResponseEntity.ok(ApiResponse.success(data, "Stop skipped"));
    }
}
