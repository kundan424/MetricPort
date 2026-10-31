package com.platform.controlplane.tenant.controller;

import com.platform.controlplane.identity.security.UserPrincipal;
import com.platform.controlplane.tenant.context.TenantContext;
import com.platform.controlplane.tenant.dto.AddMemberRequest;
import com.platform.controlplane.tenant.dto.CreateTenantRequest;
import com.platform.controlplane.tenant.dto.MembershipResponse;
import com.platform.controlplane.tenant.dto.TenantResponse;
import com.platform.controlplane.tenant.dto.UpdateRoleRequest;
import com.platform.controlplane.tenant.dto.UpdateTenantRequest;
import com.platform.controlplane.tenant.exception.TenantAccessDeniedException;
import com.platform.controlplane.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        TenantResponse response = tenantService.createTenant(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TenantResponse>> getUserTenants(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<TenantResponse> tenants = tenantService.getUserTenants(principal.getId());
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/current")
    public ResponseEntity<TenantResponse> getCurrentTenant(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID activeTenantId = TenantContext.getTenantId()
                .orElseThrow(() -> new TenantAccessDeniedException("Active tenant context is missing. Provide X-Tenant-ID header."));
        TenantResponse tenant = tenantService.getTenantById(activeTenantId, principal.getId());
        return ResponseEntity.ok(tenant);
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> getTenantById(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        TenantResponse tenant = tenantService.getTenantById(tenantId, principal.getId());
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<TenantResponse> updateTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        TenantResponse updated = tenantService.updateTenant(tenantId, request, principal.getId());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> deleteTenant(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        tenantService.deleteTenant(tenantId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tenantId}/members")
    public ResponseEntity<List<MembershipResponse>> getTenantMembers(
            @PathVariable UUID tenantId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<MembershipResponse> members = tenantService.getTenantMembers(tenantId, principal.getId());
        return ResponseEntity.ok(members);
    }

    @PostMapping("/{tenantId}/members")
    public ResponseEntity<MembershipResponse> addTenantMember(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MembershipResponse response = tenantService.addTenantMember(tenantId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{tenantId}/members/{userId}")
    public ResponseEntity<MembershipResponse> updateMemberRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MembershipResponse response = tenantService.updateMemberRole(tenantId, userId, request, principal.getId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{tenantId}/members/{userId}")
    public ResponseEntity<Void> removeTenantMember(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        tenantService.removeTenantMember(tenantId, userId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
