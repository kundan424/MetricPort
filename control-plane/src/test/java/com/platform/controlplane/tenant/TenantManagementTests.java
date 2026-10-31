package com.platform.controlplane.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controlplane.identity.entity.User;
import com.platform.controlplane.identity.repository.UserRepository;
import com.platform.controlplane.identity.service.JwtService;
import com.platform.controlplane.tenant.dto.AddMemberRequest;
import com.platform.controlplane.tenant.dto.CreateTenantRequest;
import com.platform.controlplane.tenant.enums.TenantRole;
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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantManagementTests {

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

    private User ownerUser;
    private String ownerToken;

    private User otherUser;
    private String otherToken;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();

        ownerUser = userRepository.saveAndFlush(new User("owner@example.com", passwordEncoder.encode("Password123"), "Owner"));
        ownerToken = jwtService.generateToken(ownerUser.getId(), ownerUser.getEmail());

        otherUser = userRepository.saveAndFlush(new User("other@example.com", passwordEncoder.encode("Password123"), "Other User"));
        otherToken = jwtService.generateToken(otherUser.getId(), otherUser.getEmail());
    }

    @Test
    @DisplayName("Should create tenant and assign caller as OWNER")
    void testCreateTenantSuccess() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("ShopSphere", "shopsphere");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("ShopSphere"))
                .andExpect(jsonPath("$.slug").value("shopsphere"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("OWNER"));

        assertThat(tenantRepository.existsBySlug("shopsphere")).isTrue();
        UUID tenantId = tenantRepository.findBySlug("shopsphere").orElseThrow().getId();
        assertThat(membershipRepository.existsByTenantIdAndUserIdAndRole(tenantId, ownerUser.getId(), TenantRole.OWNER)).isTrue();
    }

    @Test
    @DisplayName("Should return 409 Conflict when tenant slug already exists")
    void testCreateTenantDuplicateSlugFails() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("First Shop", "duplicate-slug");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        CreateTenantRequest duplicateRequest = new CreateTenantRequest("Second Shop", "duplicate-slug");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tenant slug is already taken: duplicate-slug"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when tenant slug format is invalid")
    void testCreateTenantInvalidSlugFormatFails() throws Exception {
        CreateTenantRequest request = new CreateTenantRequest("Invalid Shop", "Shop_Sphere 123!");

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").isNotEmpty());
    }

    @Test
    @DisplayName("Should retrieve only the tenants associated with the authenticated user")
    void testGetUserTenants() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Tenant One", "tenant-one"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Tenant Two", "tenant-two"))))
                .andExpect(status().isCreated());

        // otherUser creates one tenant
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Other Tenant", "other-tenant"))))
                .andExpect(status().isCreated());

        // ownerUser should see only their 2 tenants
        mockMvc.perform(get("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // otherUser should see only their 1 tenant
        mockMvc.perform(get("/api/v1/tenants")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value("other-tenant"));
    }

    @Test
    @DisplayName("Should return 403 Forbidden when user attempts to access a tenant they do not belong to")
    void testGetTenantByIdUnauthorizedFails() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Private Tenant", "private-tenant"))))
                .andExpect(status().isCreated());

        UUID tenantId = tenantRepository.findBySlug("private-tenant").orElseThrow().getId();

        // owner can access
        mockMvc.perform(get("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("private-tenant"));

        // otherUser cannot access
        mockMvc.perform(get("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this tenant"));
    }

    @Test
    @DisplayName("Should successfully add a new member to tenant and retrieve member list")
    void testAddMemberAndGetMembers() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("FinTrack", "fintrack"))))
                .andExpect(status().isCreated());

        UUID tenantId = tenantRepository.findBySlug("fintrack").orElseThrow().getId();

        // Add otherUser as DEVELOPER
        AddMemberRequest addRequest = new AddMemberRequest("other@example.com", TenantRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("other@example.com"))
                .andExpect(jsonPath("$.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Both members should appear in member list
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Should return 409 Conflict when adding an already existing member")
    void testAddDuplicateMemberFails() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("DataPilot", "datapilot"))))
                .andExpect(status().isCreated());

        UUID tenantId = tenantRepository.findBySlug("datapilot").orElseThrow().getId();

        // Trying to add owner again
        AddMemberRequest addRequest = new AddMemberRequest("owner@example.com", TenantRole.ADMIN);
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User is already a member of this tenant: owner@example.com"));
    }

    @Test
    @DisplayName("Should return 404 Not Found when adding non-existent user email")
    void testAddNonExistentUserFails() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Acme", "acme-test"))))
                .andExpect(status().isCreated());

        UUID tenantId = tenantRepository.findBySlug("acme-test").orElseThrow().getId();

        AddMemberRequest addRequest = new AddMemberRequest("nobody@example.com", TenantRole.VIEWER);
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No registered user found with email: nobody@example.com"));
    }

    @Test
    @DisplayName("Should reject unauthenticated tenant management requests with 401")
    void testUnauthenticatedRequestFails() throws Exception {
        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("No Auth", "no-auth"))))
                .andExpect(status().isUnauthorized());
    }
}
