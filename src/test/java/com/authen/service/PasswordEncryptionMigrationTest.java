package com.authen.service;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.authen.util.AesPasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link PasswordEncryptionMigration}: only plain-text passwords are encrypted at startup.
 */
@ExtendWith(MockitoExtension.class)
class PasswordEncryptionMigrationTest {

    @Mock
    private UserRepository userRepository;

    private final AesPasswordEncoder passwordEncoder =
            new AesPasswordEncoder(Base64.getEncoder().encodeToString(new byte[32]));

    private PasswordEncryptionMigration migration;

    @BeforeEach
    void setUp() {
        migration = new PasswordEncryptionMigration(userRepository, passwordEncoder);
    }

    @Test
    void should_saveNothing_when_noUsersExist() {
        //GIVEN
        when(userRepository.findAll()).thenReturn(List.of());

        //WHEN
        migration.run(null);

        //THEN
        verify(userRepository).saveAll(List.of());
    }

    @Test
    void should_encryptPassword_when_userHasPlainTextPassword() {
        //GIVEN
        User user = user("plain-pass");
        when(userRepository.findAll()).thenReturn(List.of(user));

        //WHEN
        migration.run(null);

        //THEN
        assertThat(passwordEncoder.decrypt(user.getPasswordHash())).isEqualTo("plain-pass");
        verify(userRepository).saveAll(List.of(user));
    }

    @Test
    void should_encryptOnlyPlainTextPasswords_when_usersAreMixed() {
        //GIVEN
        String alreadyEncrypted = passwordEncoder.encode("kept");
        User encryptedUser = user(alreadyEncrypted);
        User firstPlain = user("first");
        User secondPlain = user("second");
        when(userRepository.findAll()).thenReturn(List.of(firstPlain, encryptedUser, secondPlain));

        //WHEN
        migration.run(null);

        //THEN
        assertThat(encryptedUser.getPasswordHash()).isEqualTo(alreadyEncrypted);
        assertThat(passwordEncoder.decrypt(firstPlain.getPasswordHash())).isEqualTo("first");
        assertThat(passwordEncoder.decrypt(secondPlain.getPasswordHash())).isEqualTo("second");
        verify(userRepository).saveAll(List.of(firstPlain, secondPlain));
    }

    private User user(String storedPassword) {
        User user = new User();
        user.setPasswordHash(storedPassword);
        return user;
    }
}
