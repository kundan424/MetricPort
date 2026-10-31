package com.platform.controlplane.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controlplane.identity.entity.User;
import com.platform.controlplane.identity.repository.UserRepository;
import com.platform.controlplane.identity.service.JwtService;
import com.platform.controlplane.tenant.context.TenantContext;
import com.platform.controlplane.tenant.context.TenantContextFilter;
import com.platform.controlplane.tenant.dto.AddMemberRequest;
import com.platform.controlplane.tenant.dto.CreateTenantRequest;
import com.platform.controlplane.tenant.dto.UpdateTenantRequest;
import com.platform.controlplane.tenant.entity.Membership;
import com.platform.controlplane.tenant.entity.Tenant;
import com.platform.controlplane.tenant.enums.MembershipStatus;
import com.platform.controlplane.tenant.enums.TenantRole;
import com.platform.controlplane.tenant.enums.TenantStatus;
import com.platform.controlplane.tenant.repository.MembershipRepository;
import com.platform.controlplane.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantIsolationTests {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User userA;
    private String tokenA;
    private Tenant tenantA;

    private User userB;
    private String tokenB;
    private Tenant tenantB;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();

        // User A & Tenant A
        userA = userRepository.saveAndFlush(new User("usera@company-a.com", passwordEncoder.encode("password123"), "User A"));
        tokenA = jwtService.generateToken(userA.getId(), userA.getEmail());

        tenantA = tenantRepository.saveAndFlush(new Tenant("Company A", "company-a", TenantStatus.ACTIVE));
        membershipRepository.saveAndFlush(new Membership(tenantA, userA, TenantRole.OWNER, MembershipStatus.ACTIVE));

        // User B & Tenant B
        userB = userRepository.saveAndFlush(new User("userb@company-b.com", passwordEncoder.encode("password123"), "User B"));
        tokenB = jwtService.generateToken(userB.getId(), userB.getEmail());

        tenantB = tenantRepository.saveAndFlush(new Tenant("Company B", "company-b", TenantStatus.ACTIVE));
        membershipRepository.saveAndFlush(new Membership(tenantB, userB, TenantRole.OWNER, MembershipStatus.ACTIVE));
    }

    @Test
    @DisplayName("User can resolve active tenant context via valid X-Tenant-ID header")
    void testResolveActiveTenantContext() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/current")
                        .header("Authorization", "Bearer " + tokenA)
                        .header(TenantContextFilter.TENANT_HEADER, tenantA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantA.getId().toString()))
                .andExpect(jsonPath("$.name").value("Company A"))
                .andExpect(jsonPath("$.slug").value("company-a"))
                .andExpect(jsonPath("$.role").value("OWNER"));

        // ThreadLocal must be clean outside the request
        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Request with malformed X-Tenant-ID header returns 400 Bad Request")
    void testMalformedTenantHeader() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/current")
                        .header("Authorization", "Bearer " + tokenA)
                        .header(TenantContextFilter.TENANT_HEADER, "invalid-uuid-format"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid X-Tenant-ID header format: invalid-uuid-format"));

        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Prevent cross-tenant header spoofing: User A cannot use Tenant B's ID in X-Tenant-ID")
    void testCrossTenantHeaderSpoofingPrevented() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/current")
                        .header("Authorization", "Bearer " + tokenA)
                        .header(TenantContextFilter.TENANT_HEADER, tenantB.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: User is not an active member of tenant: " + tenantB.getId()));

        assertThat(TenantContext.getTenantId()).isEmpty();
    }

    @Test
    @DisplayName("Prevent cross-tenant URL path tampering: User A cannot read Tenant B by ID")
    void testCrossTenantGetByIdPrevented() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/" + tenantB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this tenant"));
    }

    @Test
    @DisplayName("Prevent cross-tenant URL path tampering: User A cannot update Tenant B")
    void testCrossTenantUpdatePrevented() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/" + tenantB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTenantRequest("Hacked Company B"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this tenant"));

        // Verify Company B name was unchanged
        Tenant untouchedTenantB = tenantRepository.findById(tenantB.getId()).orElseThrow();
        assertThat(untouchedTenantB.getName()).isEqualTo("Company B");
    }

    @Test
    @DisplayName("Prevent cross-tenant URL path tampering: User A cannot delete Tenant B")
    void testCrossTenantDeletePrevented() throws Exception {
        mockMvc.perform(delete("/api/v1/tenants/" + tenantB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this tenant"));

        assertThat(tenantRepository.existsById(tenantB.getId())).isTrue();
    }

    @Test
    @DisplayName("Prevent cross-tenant member injection: User A cannot add members to Tenant B")
    void testCrossTenantAddMemberPrevented() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/" + tenantB.getId() + "/members")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest("usera@company-a.com", TenantRole.ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this tenant"));

        assertThat(membershipRepository.existsByTenantIdAndUserId(tenantB.getId(), userA.getId())).isFalse();
    }

    @Test
    @DisplayName("Suspended membership cannot access tenant context")
    void testSuspendedMembershipDenied() throws Exception {
        User userC = userRepository.saveAndFlush(new User("userc@company-a.com", passwordEncoder.encode("password123"), "User C"));
        String tokenC = jwtService.generateToken(userC.getId(), userC.getEmail());

        // Suspended membership in Tenant A
        membershipRepository.saveAndFlush(new Membership(tenantA, userC, TenantRole.VIEWER, MembershipStatus.SUSPENDED));

        // Attempt to access with header
        mockMvc.perform(get("/api/v1/tenants/current")
                        .header("Authorization", "Bearer " + tokenC)
                        .header(TenantContextFilter.TENANT_HEADER, tenantA.getId().toString()))
                .andExpect(status().isForbidden());

        // Attempt to access via path
        mockMvc.perform(get("/api/v1/tenants/" + tenantA.getId())
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Membership is not active for this tenant"));
    }

    @Test
    @DisplayName("Missing X-Tenant-ID header when calling /current returns 403 Forbidden")
    void testMissingTenantContextReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/current")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Active tenant context is missing. Provide X-Tenant-ID header."));
    }
}
