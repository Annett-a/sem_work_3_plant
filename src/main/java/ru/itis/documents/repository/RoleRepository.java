package ru.itis.documents.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.documents.domain.entity.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}