package com.example.insurancecrm.repository;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleAndActiveTrue(Role role);

    /** Every non-soft-deleted user — the operational "real" user list (Users page, agent
     *  dropdowns, unassigned-customer resolution). Soft-deleted users are excluded here even
     *  though their document still exists; see User.deletedAt. */
    List<User> findByDeletedAtIsNull();

    /** Validates a user both exists and is not soft-deleted — use this instead of plain findById
     *  wherever the result is a target being assigned/relied on going forward (e.g. a customer
     *  being handed to this agent), as opposed to resolving a historical reference. */
    Optional<User> findByIdAndDeletedAtIsNull(String id);
}
