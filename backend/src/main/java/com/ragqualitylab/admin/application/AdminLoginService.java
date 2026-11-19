package com.ragqualitylab.admin.application;

import com.ragqualitylab.admin.domain.AdminUser;
import com.ragqualitylab.admin.domain.AdminUserRepository;
import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 자격 증명 검증과 실패 잠금.
 * 실패 응답은 계정 존재 여부와 무관하게 같은 오류(INVALID_CREDENTIALS)를 반환한다.
 */
@Service
public class AdminLoginService {

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthProperties properties;
    private final Clock clock;
    // 없는 계정에도 같은 비용의 해시 비교를 수행해 응답 시간 차이를 줄인다
    private final String dummyHash;

    public AdminLoginService(AdminUserRepository repository, PasswordEncoder passwordEncoder,
                             AdminAuthProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public String authenticate(String username, String rawPassword) {
        Instant now = clock.instant();
        Optional<AdminUser> found = repository.findByUsernameForUpdate(username);
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw invalidCredentials();
        }
        AdminUser user = found.get();
        if (user.isLockedAt(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "로그인 시도가 너무 많아 잠시 잠겼습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.recordFailure(now, properties.maxFailedAttempts(), properties.lockDuration());
            throw invalidCredentials();
        }
        user.recordSuccess(now);
        return user.getUsername();
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "아이디 또는 비밀번호가 올바르지 않습니다.");
    }
}
