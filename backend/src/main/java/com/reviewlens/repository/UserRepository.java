package com.reviewlens.repository;

import com.reviewlens.model.Role;
import com.reviewlens.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    long countByActive(boolean active);
    long countByRole(Role role);
    long countByRoleAndActive(Role role, boolean active);
}
