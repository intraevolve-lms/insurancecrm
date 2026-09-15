package com.example.insurancecrm.service;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.dto.request.CreateUserRequest;
import com.example.insurancecrm.dto.response.UserResponse;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.exception.ApiException;
import com.example.insurancecrm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private CreateUserRequest request(String name, String email, String password, Role role) {
        CreateUserRequest req = new CreateUserRequest();
        req.setName(name); req.setEmail(email); req.setPassword(password); req.setRole(role);
        return req;
    }

    @Test
    void createUser_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("agent@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request("Agent", "agent@test.com", "pass", Role.AGENT)))
                .isInstanceOf(ApiException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_encodesPasswordBeforeSaving() {
        when(userRepository.existsByEmail("agent@test.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request("Agent", "agent@test.com", "plaintext", Role.AGENT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(response.getEmail()).isEqualTo("agent@test.com");
    }

    @Test
    void updateUser_missingUser_throwsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser("missing", request("A", "a@test.com", "p", Role.AGENT)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateUser_changingToAnotherUsersEmail_throwsConflict() {
        User existing = User.builder().id("user-1").email("old@test.com").role(Role.AGENT).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser("user-1", request("A", "taken@test.com", "", Role.AGENT)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateUser_keepingSameEmail_doesNotTriggerConflictCheck() {
        User existing = User.builder().id("user-1").email("same@test.com").role(Role.AGENT).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser("user-1", request("Updated Name", "same@test.com", "", Role.AGENT));

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateUser_blankPassword_doesNotOverwriteExistingPassword() {
        User existing = User.builder().id("user-1").email("same@test.com").password("original-hash").role(Role.AGENT).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser("user-1", request("Updated Name", "same@test.com", "", Role.AGENT));

        assertThat(existing.getPassword()).isEqualTo("original-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void updateUser_nonBlankPassword_reEncodesIt() {
        User existing = User.builder().id("user-1").email("same@test.com").password("original-hash").role(Role.AGENT).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser("user-1", request("Updated Name", "same@test.com", "newpass", Role.AGENT));

        assertThat(existing.getPassword()).isEqualTo("new-hash");
    }

    @Test
    void deactivateUser_setsActiveFalse() {
        User existing = User.builder().id("user-1").active(true).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deactivateUser("user-1");

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void deleteUser_inactiveUser_softDeletesIt_neverPhysicallyRemovesTheRecord() {
        User existing = User.builder().id("user-1").active(false).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.deleteUser("user-1");

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(userRepository, never()).delete(any());
        verify(userRepository).save(existing);
    }

    @Test
    void deleteUser_stillActiveUser_throwsAndDoesNotDelete() {
        User existing = User.builder().id("user-1").active(true).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.deleteUser("user-1")).isInstanceOf(ApiException.class);
        verify(userRepository, never()).delete(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_alreadyDeleted_throwsBadRequest() {
        User existing = User.builder().id("user-1").active(false).deletedAt(java.time.LocalDateTime.now()).build();
        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.deleteUser("user-1")).isInstanceOf(ApiException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_missingUser_throwsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser("missing")).isInstanceOf(ApiException.class);
    }

    @Test
    void getAllUsers_excludesDeletedUsers() {
        User active = User.builder().id("user-1").name("Active One").build();
        when(userRepository.findByDeletedAtIsNull()).thenReturn(List.of(active));

        var result = userService.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("user-1");
        verify(userRepository, never()).findAll();
    }

    @Test
    void findById_missingUser_throwsNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById("missing")).isInstanceOf(ApiException.class);
    }
}
