package com.platform.controlplane.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.controlplane.identity.dto.LoginRequest;
import com.platform.controlplane.identity.dto.RegisterRequest;
import com.platform.controlplane.identity.entity.User;
import com.platform.controlplane.identity.repository.UserRepository;
import com.platform.controlplane.identity.service.JwtService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Milestone2HardeningTests {

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

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        tenantRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Email normalization: user can register with mixed case and login with lowercase")
    void testEmailNormalization() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("MixedCase.User@Domain.COM", "password123", "Mixed User");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("mixedcase.user@domain.com"));

        // Login using lowercase
        LoginRequest loginRequest = new LoginRequest("mixedcase.user@domain.com", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value("mixedcase.user@domain.com"));
    }

    @Test
    @DisplayName("Tampered JWT signature is rejected with 401 Unauthorized")
    void testTamperedJwtSignature() throws Exception {
        User user = userRepository.saveAndFlush(new User("token-tamper@corp.com", passwordEncoder.encode("pass123"), "Tamper User"));
        String validToken = jwtService.generateToken(user.getId(), user.getEmail());

        // Tamper with signature by mutating last 4 characters
        String tamperedToken = validToken.substring(0, validToken.length() - 4) + "XXXX";

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Database-level unique constraint on users(email) is enforced")
    void testDatabaseLevelUserEmailUniqueConstraint() {
        userRepository.saveAndFlush(new User("duplicate@check.com", "hash1", "User One"));

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(new User("duplicate@check.com", "hash2", "User Two"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database-level unique constraint on tenants(slug) is enforced")
    void testDatabaseLevelTenantSlugUniqueConstraint() {
        tenantRepository.saveAndFlush(new Tenant("Slug One", "unique-slug", TenantStatus.ACTIVE));

        assertThatThrownBy(() -> {
            tenantRepository.saveAndFlush(new Tenant("Slug Two", "unique-slug", TenantStatus.ACTIVE));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Database-level compound unique constraint on memberships(tenant_id, user_id) is enforced")
    void testDatabaseLevelMembershipUniqueConstraint() {
        User user = userRepository.saveAndFlush(new User("member-uq@corp.com", "hash", "Member"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("UQ Tenant", "uq-tenant", TenantStatus.ACTIVE));

        membershipRepository.saveAndFlush(new Membership(tenant, user, TenantRole.DEVELOPER, MembershipStatus.ACTIVE));

        assertThatThrownBy(() -> {
            membershipRepository.saveAndFlush(new Membership(tenant, user, TenantRole.VIEWER, MembershipStatus.ACTIVE));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Non-member cannot view tenant membership list")
    void testNonMemberCannotViewTenantMembers() throws Exception {
        User ownerA = userRepository.saveAndFlush(new User("ownera@domain.com", passwordEncoder.encode("pass"), "Owner A"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A", "tenant-a", TenantStatus.ACTIVE));
        membershipRepository.saveAndFlush(new Membership(tenantA, ownerA, TenantRole.OWNER, MembershipStatus.ACTIVE));

        User intruder = userRepository.saveAndFlush(new User("intruder@domain.com", passwordEncoder.encode("pass"), "Intruder"));
        String intruderToken = jwtService.generateToken(intruder.getId(), intruder.getEmail());

        mockMvc.perform(get("/api/v1/tenants/" + tenantA.getId() + "/members")
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have access to this tenant"));
    }
}
