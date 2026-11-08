package com.core.controller;

import com.common.dto.ApiResponse;
import com.core.dto.admin.AdminActiveRequest;
import com.core.dto.admin.AdminAreaRequest;
import com.core.dto.admin.AdminAreaResponse;
import com.core.dto.admin.AdminJobResponse;
import com.core.dto.admin.AdminOrderResponse;
import com.core.dto.admin.AdminPasswordRequest;
import com.core.dto.admin.AdminUserResponse;
import com.core.service.admin.AdminAddressService;
import com.core.service.admin.AdminService;
import com.master.dto.PageResponse;
import com.core.service.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin menu (docs/user-stories/admin.md) — role ADMIN only: db/permissions-admin-data.sql for the filter, and
 * {@link AdminService} checks the role again. The path variable names are part of the permission rows.
 */
@RestController
@RequestMapping("/endpoint-core-service/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "System admin: users, orders and jobs of every account")
public class AdminController {

    private final AdminService adminService;
    private final AdminAddressService adminAddressService;
    private final SecurityService securityService;

    // ---- users ----

    @Operation(summary = "List users", description = "Not deleted; q = part of username / name (max 200)")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> listUsers(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(adminService.listUsers(admin(authHeader), q)));
    }

    @Operation(summary = "Disable a user", description = "Cannot log in; open sessions end now")
    @PatchMapping("/users/{userId}/disable")
    public ResponseEntity<ApiResponse<AdminUserResponse>> disableUser(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer userId) {
        return ResponseEntity.ok(ApiResponse.success(adminService.disableUser(admin(authHeader), userId)));
    }

    @Operation(summary = "Enable a user")
    @PatchMapping("/users/{userId}/enable")
    public ResponseEntity<ApiResponse<AdminUserResponse>> enableUser(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer userId) {
        return ResponseEntity.ok(ApiResponse.success(adminService.enableUser(admin(authHeader), userId)));
    }

    @Operation(summary = "Set a new password", description = "Ends every session of the user")
    @PutMapping("/users/{userId}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer userId,
            @Valid @RequestBody AdminPasswordRequest request) {
        adminService.changePassword(admin(authHeader), userId, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed"));
    }

    @Operation(summary = "Delete a user (soft)",
            description = "Disabled + hidden; team rows end, pending job requests cancelled. Refused while the user has open jobs.")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Integer userId) {
        adminService.deleteUser(admin(authHeader), userId);
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted"));
    }

    // ---- orders ----

    @Operation(summary = "List orders", description = "Active orders of every owner; q = part of order code / owner (max 100)")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<AdminOrderResponse>>> listOrders(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(adminService.listOrders(admin(authHeader), q)));
    }

    @Operation(summary = "Reset an order to NEW", description = "Refused while the order is on a job that is not closed")
    @PatchMapping("/orders/{orderId}/reset")
    public ResponseEntity<ApiResponse<Void>> resetOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId) {
        adminService.resetOrder(admin(authHeader), orderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Order reset"));
    }

    @Operation(summary = "Delete an order (soft)", description = "Refused while the order is on a job that is not closed")
    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long orderId) {
        adminService.deleteOrder(admin(authHeader), orderId);
        return ResponseEntity.ok(ApiResponse.success(null, "Order deleted"));
    }

    // ---- jobs ----

    @Operation(summary = "List jobs", description = "Active jobs of every owner; q = part of job code / name / owner (max 100)")
    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<AdminJobResponse>>> listJobs(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.success(adminService.listJobs(admin(authHeader), q)));
    }

    @Operation(summary = "Reset a job to NEW", description = "Every stop back to NEW, every order back to ASSIGNED")
    @PatchMapping("/jobs/{jobId}/reset")
    public ResponseEntity<ApiResponse<Void>> resetJob(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId) {
        adminService.resetJob(admin(authHeader), jobId);
        return ResponseEntity.ok(ApiResponse.success(null, "Job reset"));
    }

    @Operation(summary = "Delete a job (soft)", description = "Same as the owner deleting it")
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<Void>> deleteJob(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId) {
        adminService.deleteJob(admin(authHeader), jobId);
        return ResponseEntity.ok(ApiResponse.success(null, "Job deleted"));
    }

    // ---- address master data (US-ADM-06) ----

    @Operation(summary = "List provinces", description = "All (disabled included); q = part of code / name; paged, size max 100")
    @GetMapping("/address/provinces")
    public ResponseEntity<ApiResponse<PageResponse<AdminAreaResponse>>> listProvinces(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminAddressService.listProvinces(admin(authHeader), q, page, size)));
    }

    @Operation(summary = "Add a province", description = "code: 2 digits, unique")
    @PostMapping("/address/provinces")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> createProvince(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AdminAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminAddressService.createProvince(admin(authHeader), request)));
    }

    @Operation(summary = "Rename a province", description = "Only the name; code cannot change")
    @PutMapping("/address/provinces/{provinceId}")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> updateProvince(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long provinceId,
            @Valid @RequestBody AdminAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.updateProvince(admin(authHeader), provinceId, request)));
    }

    @Operation(summary = "Disable / enable a province", description = "Disabled = hidden from the address pickers")
    @PatchMapping("/address/provinces/{provinceId}/status")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> setProvinceActive(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long provinceId,
            @Valid @RequestBody AdminActiveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.setProvinceActive(admin(authHeader), provinceId, request.getActive())));
    }

    @Operation(summary = "List districts", description = "Optional provinceId; q = part of code / name; paged")
    @GetMapping("/address/districts")
    public ResponseEntity<ApiResponse<PageResponse<AdminAreaResponse>>> listDistricts(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.listDistricts(admin(authHeader), provinceId, q, page, size)));
    }

    @Operation(summary = "Add a district", description = "parentId = provinceId; code: 4 digits starting with the province code")
    @PostMapping("/address/districts")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> createDistrict(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AdminAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminAddressService.createDistrict(admin(authHeader), request)));
    }

    @Operation(summary = "Edit a district", description = "Name, lat / lng; code and province cannot change")
    @PutMapping("/address/districts/{districtId}")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> updateDistrict(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long districtId,
            @Valid @RequestBody AdminAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.updateDistrict(admin(authHeader), districtId, request)));
    }

    @Operation(summary = "Disable / enable a district", description = "Enable refused while its province is disabled")
    @PatchMapping("/address/districts/{districtId}/status")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> setDistrictActive(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long districtId,
            @Valid @RequestBody AdminActiveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.setDistrictActive(admin(authHeader), districtId, request.getActive())));
    }

    @Operation(summary = "List subdistricts", description = "Optional districtId; q = part of code / name / zip code; paged")
    @GetMapping("/address/subdistricts")
    public ResponseEntity<ApiResponse<PageResponse<AdminAreaResponse>>> listSubdistricts(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.listSubdistricts(admin(authHeader), districtId, q, page, size)));
    }

    @Operation(summary = "Add a subdistrict",
            description = "parentId = districtId; code: 6 digits starting with the district code; zip 5 digits or empty")
    @PostMapping("/address/subdistricts")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> createSubdistrict(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AdminAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminAddressService.createSubdistrict(admin(authHeader), request)));
    }

    @Operation(summary = "Edit a subdistrict", description = "Name, zip code, lat / lng; code and district cannot change")
    @PutMapping("/address/subdistricts/{subdistrictId}")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> updateSubdistrict(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long subdistrictId,
            @Valid @RequestBody AdminAreaRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.updateSubdistrict(admin(authHeader), subdistrictId, request)));
    }

    @Operation(summary = "Disable / enable a subdistrict", description = "Enable refused while its district is disabled")
    @PatchMapping("/address/subdistricts/{subdistrictId}/status")
    public ResponseEntity<ApiResponse<AdminAreaResponse>> setSubdistrictActive(
            @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader,
            @PathVariable Long subdistrictId,
            @Valid @RequestBody AdminActiveRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAddressService.setSubdistrictActive(admin(authHeader), subdistrictId, request.getActive())));
    }

    private String admin(String authHeader) {
        return securityService.extractUsernameFromToken(authHeader);
    }
}
