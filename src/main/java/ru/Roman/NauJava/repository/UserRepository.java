package ru.Roman.NauJava.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.Roman.NauJava.domain.entity.User;

import java.util.Optional;

/**
 * Репозиторий пользователей с поиском по логину и email.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}

