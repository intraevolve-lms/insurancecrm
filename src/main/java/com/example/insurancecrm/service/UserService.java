package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateUserRequest;
import com.example.insurancecrm.dto.response.UserResponse;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    public UserResponse getUserById(String id) {
        return toResponse(findById(id));
    }

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("Email already in use: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(userRepository.save(user));
    }

    public UserResponse updateUser(String id, CreateUserRequest request) {
        User user = findById(id);

        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("Email already in use: " + request.getEmail());
        }

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return toResponse(userRepository.save(user));
    }

    public void deactivateUser(String id) {
        User user = findById(id);
        user.setActive(false);
        userRepository.save(user);
    }

    /** Immediately invalidates every active/refresh token for the given agents, regardless of remaining expiry. */
    public void forceLogout(List<String> userIds) {
        List<User> agents = userRepository.findAllById(userIds).stream()
                .filter(u -> u.getRole() == Role.AGENT)
                .toList();

        if (agents.isEmpty()) {
            throw ApiException.badRequest("No matching agent accounts found to log out");
        }

        LocalDateTime now = LocalDateTime.now();
        agents.forEach(u -> u.setTokensInvalidBefore(now));
        userRepository.saveAll(agents);
    }

    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
