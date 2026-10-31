package com.platform.controlplane.tenant.repository;

import com.platform.controlplane.tenant.entity.Membership;
import com.platform.controlplane.tenant.enums.TenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    List<Membership> findByUserId(UUID userId);

    List<Membership> findByTenantId(UUID tenantId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.tenant WHERE m.user.id = :userId")
    List<Membership> findByUserIdWithTenant(@Param("userId") UUID userId);

    @Query("SELECT m FROM Membership m JOIN FETCH m.user WHERE m.tenant.id = :tenantId")
    List<Membership> findByTenantIdWithUser(@Param("tenantId") UUID tenantId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserIdAndRole(UUID tenantId, UUID userId, TenantRole role);

    long countByTenantIdAndRole(UUID tenantId, TenantRole role);

    void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
