package ru.Roman.NauJava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.Roman.NauJava.domain.entity.User;
import ru.Roman.NauJava.domain.enums.UserRole;
import ru.Roman.NauJava.repository.UserRepository;

@SpringBootApplication
public class NauJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(NauJavaApplication.class, args);
	}

	/**
	 * Создаёт администратора по умолчанию для быстрого старта.
	 */
	@Bean
	public CommandLineRunner dataInitializer(UserRepository userRepository,
											 PasswordEncoder passwordEncoder) {
		return args -> {
			if (userRepository.existsByUsername("admin")) {
				return;
			}
			User admin = User.builder()
					.username("admin")
					.email("admin@example.com")
					.passwordHash(passwordEncoder.encode("admin123"))
					.role(UserRole.ADMIN)
					.build();
			userRepository.save(admin);
		};
	}
}
