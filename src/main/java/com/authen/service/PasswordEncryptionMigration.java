package com.authen.service;

import com.authen.entity.User;
import com.authen.repository.UserRepository;
import com.authen.util.AesPasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Encrypts any password still stored as plain text (rows from before encryption was introduced,
 * or inserted by db/roles-users-data.sql) every time the application starts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordEncryptionMigration implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AesPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> plainTextUsers = userRepository.findAll().stream()
                .filter(user -> passwordEncoder.upgradeEncoding(user.getPasswordHash()))
                .toList();

        plainTextUsers.forEach(user -> user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash())));
        userRepository.saveAll(plainTextUsers);

        if (!plainTextUsers.isEmpty()) {
            log.info("Encrypted {} plain-text password(s)", plainTextUsers.size());
        }
    }
}
