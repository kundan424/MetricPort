package com.platform.controlplane.domain;

import com.platform.controlplane.identity.entity.User;
import com.platform.controlplane.identity.repository.UserRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class IdentityAndTenantDomainTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully persist and retrieve a User")
    void testCreateAndFindUser() {
        User user = new User("developer@example.com", "hashed_pwd_123", "Alice Developer");
        User savedUser = userRepository.saveAndFlush(user);

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
        assertThat(savedUser.isEnabled()).isTrue();

        Optional<User> found = userRepository.findByEmail("developer@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Alice Developer");
    }

    @Test
    @DisplayName("Should reject duplicate user email")
    void testDuplicateUserEmailFails() {
        User user1 = new User("duplicate@example.com", "hash1", "User One");
        userRepository.saveAndFlush(user1);

        User user2 = new User("duplicate@example.com", "hash2", "User Two");
        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should successfully persist and retrieve a Tenant")
    void testCreateAndFindTenant() {
        Tenant tenant = new Tenant("Acme Corp", "acme-corp");
        Tenant savedTenant = tenantRepository.saveAndFlush(tenant);

        assertThat(savedTenant.getId()).isNotNull();
        assertThat(savedTenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(savedTenant.getCreatedAt()).isNotNull();

        Optional<Tenant> found = tenantRepository.findBySlug("acme-corp");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("Should reject duplicate tenant slug")
    void testDuplicateTenantSlugFails() {
        Tenant tenant1 = new Tenant("Acme Primary", "acme");
        tenantRepository.saveAndFlush(tenant1);

        Tenant tenant2 = new Tenant("Acme Secondary", "acme");
        assertThatThrownBy(() -> tenantRepository.saveAndFlush(tenant2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should establish valid Membership between User and Tenant with Role")
    void testCreateMembership() {
        User user = userRepository.saveAndFlush(new User("owner@example.com", "hash", "Tenant Owner"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("FinTrack", "fintrack"));

        Membership membership = new Membership(tenant, user, TenantRole.OWNER);
        Membership savedMembership = membershipRepository.saveAndFlush(membership);

        assertThat(savedMembership.getId()).isNotNull();
        assertThat(savedMembership.getRole()).isEqualTo(TenantRole.OWNER);
        assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        Optional<Membership> found = membershipRepository.findByTenantIdAndUserId(tenant.getId(), user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRole()).isEqualTo(TenantRole.OWNER);
        assertThat(found.get().getUser().getEmail()).isEqualTo("owner@example.com");
        assertThat(found.get().getTenant().getSlug()).isEqualTo("fintrack");
    }

    @Test
    @DisplayName("Should prevent duplicate membership for same user in same tenant")
    void testDuplicateMembershipFails() {
        User user = userRepository.saveAndFlush(new User("dev@example.com", "hash", "Dev"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("TechCorp", "techcorp"));

        Membership membership1 = new Membership(tenant, user, TenantRole.DEVELOPER);
        membershipRepository.saveAndFlush(membership1);

        Membership membership2 = new Membership(tenant, user, TenantRole.ADMIN);
        assertThatThrownBy(() -> membershipRepository.saveAndFlush(membership2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should allow single user to hold memberships in multiple distinct tenants")
    void testUserInMultipleTenants() {
        User user = userRepository.saveAndFlush(new User("consultant@example.com", "hash", "Consultant"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Company A", "comp-a"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Company B", "comp-b"));

        membershipRepository.saveAndFlush(new Membership(tenantA, user, TenantRole.ADMIN));
        membershipRepository.saveAndFlush(new Membership(tenantB, user, TenantRole.VIEWER));

        List<Membership> memberships = membershipRepository.findByUserId(user.getId());
        assertThat(memberships).hasSize(2);
        assertThat(membershipRepository.existsByTenantIdAndUserIdAndRole(tenantA.getId(), user.getId(), TenantRole.ADMIN)).isTrue();
        assertThat(membershipRepository.existsByTenantIdAndUserIdAndRole(tenantB.getId(), user.getId(), TenantRole.VIEWER)).isTrue();
    }
}
