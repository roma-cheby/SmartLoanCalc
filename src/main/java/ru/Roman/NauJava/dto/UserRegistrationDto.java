package ru.Roman.NauJava.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO для регистрации нового пользователя.
 */
@Data
public class UserRegistrationDto {

    @NotBlank(message = "Логин обязателен")
    @Size(min = 3, max = 32, message = "Логин 3-32 символа")
    private String username;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, max = 72, message = "Пароль от 6 символов")
    private String password;

    @NotBlank(message = "Повтор пароля обязателен")
    private String confirmPassword;
}

