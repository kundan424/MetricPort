package com.platform.controlplane.tenant.context;

import com.platform.controlplane.identity.security.UserPrincipal;
import com.platform.controlplane.identity.service.JwtService;
import com.platform.controlplane.tenant.entity.Membership;
import com.platform.controlplane.tenant.enums.MembershipStatus;
import com.platform.controlplane.tenant.repository.MembershipRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    private final MembershipRepository membershipRepository;
    private final JwtService jwtService;

    public TenantContextFilter(MembershipRepository membershipRepository, JwtService jwtService) {
        this.membershipRepository = membershipRepository;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String tenantHeader = request.getHeader(TENANT_HEADER);

        try {
            if (StringUtils.hasText(tenantHeader)) {
                UUID tenantId;
                try {
                    tenantId = UUID.fromString(tenantHeader.trim());
                } catch (IllegalArgumentException e) {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid " + TENANT_HEADER + " header format: " + tenantHeader);
                    return;
                }

                UUID userId = resolveAuthenticatedUserId(request);
                if (userId != null) {
                    Optional<Membership> membership = membershipRepository.findByTenantIdAndUserId(tenantId, userId);

                    if (membership.isEmpty() || membership.get().getStatus() != MembershipStatus.ACTIVE) {
                        sendError(response, HttpServletResponse.SC_FORBIDDEN,
                                "Access denied: User is not an active member of tenant: " + tenantId);
                        return;
                    }

                    TenantContext.setCurrentTenant(tenantId, membership.get().getRole());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveAuthenticatedUserId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }

        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.validateToken(token)) {
                return jwtService.extractUserId(token);
            }
        }

        return null;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\",\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
