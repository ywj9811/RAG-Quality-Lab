package com.ragqualitylab.admin.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUsername(String username);

    /** 동시 로그인 실패가 횟수를 덮어쓰지 않도록 행 잠금으로 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AdminUser u where u.username = :username")
    Optional<AdminUser> findByUsernameForUpdate(String username);
}
