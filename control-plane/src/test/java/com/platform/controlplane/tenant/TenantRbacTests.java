package com.platform.controlplane.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controlplane.identity.entity.User;
import com.platform.controlplane.identity.repository.UserRepository;
import com.platform.controlplane.identity.service.JwtService;
import com.platform.controlplane.tenant.dto.AddMemberRequest;
import com.platform.controlplane.tenant.dto.CreateTenantRequest;
import com.platform.controlplane.tenant.dto.UpdateRoleRequest;
import com.platform.controlplane.tenant.dto.UpdateTenantRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantRbacTests {

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

    private User owner;
    private String ownerToken;

    private User admin;
    private String adminToken;

    private User developer;
    private String developerToken;

    private User viewer;
    private String viewerToken;

    private UUID tenantId;

    @BeforeEach
    void setUp() throws Exception {
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.saveAndFlush(new User("owner@corp.com", passwordEncoder.encode("pass123"), "Owner"));
        ownerToken = jwtService.generateToken(owner.getId(), owner.getEmail());

        admin = userRepository.saveAndFlush(new User("admin@corp.com", passwordEncoder.encode("pass123"), "Admin"));
        adminToken = jwtService.generateToken(admin.getId(), admin.getEmail());

        developer = userRepository.saveAndFlush(new User("dev@corp.com", passwordEncoder.encode("pass123"), "Developer"));
        developerToken = jwtService.generateToken(developer.getId(), developer.getEmail());

        viewer = userRepository.saveAndFlush(new User("viewer@corp.com", passwordEncoder.encode("pass123"), "Viewer"));
        viewerToken = jwtService.generateToken(viewer.getId(), viewer.getEmail());

        // Create tenant by owner
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Corp Inc", "corp-inc"))))
                .andExpect(status().isCreated());

        tenantId = tenantRepository.findBySlug("corp-inc").orElseThrow().getId();

        // Add admin, developer, viewer by owner
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest("admin@corp.com", TenantRole.ADMIN))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest("dev@corp.com", TenantRole.DEVELOPER))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest("viewer@corp.com", TenantRole.VIEWER))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("OWNER should be permitted to update tenant, change roles, remove members, and delete tenant")
    void testOwnerPermissions() throws Exception {
        // Owner updates tenant name
        mockMvc.perform(put("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTenantRequest("Corp International"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Corp International"));

        // Owner promotes developer to ADMIN
        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/members/" + developer.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(TenantRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Owner removes viewer
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId + "/members/" + viewer.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(membershipRepository.existsByTenantIdAndUserId(tenantId, viewer.getId())).isFalse();

        // Owner deletes tenant
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        assertThat(tenantRepository.existsById(tenantId)).isFalse();
    }

    @Test
    @DisplayName("ADMIN should manage non-owner roles but cannot promote to OWNER or delete tenant")
    void testAdminPermissionsAndRestrictions() throws Exception {
        // Admin CAN update tenant name
        mockMvc.perform(put("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTenantRequest("Admin Rename Corp"))))
                .andExpect(status().isOk());

        // Admin CANNOT delete tenant
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Action requires at least OWNER role, but current role is ADMIN"));

        // Admin CANNOT promote developer to OWNER
        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/members/" + developer.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(TenantRole.OWNER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ADMIN cannot promote a member to OWNER"));

        // Admin CANNOT modify OWNER's role
        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/members/" + owner.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(TenantRole.DEVELOPER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ADMIN cannot modify an OWNER's role"));

        // Admin CANNOT remove OWNER
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId + "/members/" + owner.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ADMIN cannot remove an OWNER"));

        // Admin CAN update developer role to VIEWER
        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/members/" + developer.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(TenantRole.VIEWER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    @DisplayName("DEVELOPER should be restricted from tenant administration and member modifications")
    void testDeveloperRestrictions() throws Exception {
        // Developer CAN view tenant
        mockMvc.perform(get("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));

        // Developer CAN view members
        mockMvc.perform(get("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk());

        // Developer CANNOT update tenant
        mockMvc.perform(put("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTenantRequest("Hacked Corp"))))
                .andExpect(status().isForbidden());

        // Developer CANNOT delete tenant
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());

        // Developer CANNOT add members
        User newUser = userRepository.saveAndFlush(new User("newbie@corp.com", passwordEncoder.encode("pass"), "Newbie"));
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest("newbie@corp.com", TenantRole.VIEWER))))
                .andExpect(status().isForbidden());

        // Developer CANNOT modify roles
        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/members/" + viewer.getId())
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(TenantRole.ADMIN))))
                .andExpect(status().isForbidden());

        // Developer CANNOT remove other members
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId + "/members/" + viewer.getId())
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER should only have read access to tenant and members")
    void testViewerRestrictions() throws Exception {
        // Viewer CAN view tenant
        mockMvc.perform(get("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));

        // Viewer CANNOT update tenant
        mockMvc.perform(put("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTenantRequest("Viewer Corp"))))
                .andExpect(status().isForbidden());

        // Viewer CANNOT add members
        mockMvc.perform(post("/api/v1/tenants/" + tenantId + "/members")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest("test@test.com", TenantRole.VIEWER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sole OWNER cannot demote themselves or leave tenant without transferring ownership")
    void testSoleOwnerCannotLeaveOrDemote() throws Exception {
        // Demoting sole owner
        mockMvc.perform(put("/api/v1/tenants/" + tenantId + "/members/" + owner.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRoleRequest(TenantRole.ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Cannot demote the sole OWNER of a tenant. Transfer ownership first."));

        // Sole owner leaving tenant
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId + "/members/" + owner.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Sole owner cannot leave tenant without assigning another owner"));
    }

    @Test
    @DisplayName("Role permissions must be isolated per tenant (user is OWNER in Tenant A, but VIEWER in Tenant B)")
    void testMultiTenantRoleIsolation() throws Exception {
        // Admin user creates their own separate tenant, becoming OWNER of Tenant B
        mockMvc.perform(post("/api/v1/tenants")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest("Admin Personal Tenant", "admin-personal"))))
                .andExpect(status().isCreated());

        UUID adminPersonalTenantId = tenantRepository.findBySlug("admin-personal").orElseThrow().getId();

        // In Tenant B (admin-personal), admin user is OWNER -> can delete it
        mockMvc.perform(delete("/api/v1/tenants/" + adminPersonalTenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // In Tenant A (corp-inc), admin user is still ADMIN -> CANNOT delete it
        mockMvc.perform(delete("/api/v1/tenants/" + tenantId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }
}
