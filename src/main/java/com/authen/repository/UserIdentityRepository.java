package com.authen.repository;

import com.authen.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Integer> {

    Optional<UserIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserIdentity> findByUserIdAndProvider(Integer userId, String provider);
}
