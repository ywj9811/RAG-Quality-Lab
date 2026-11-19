package com.ragqualitylab.admin.application;

import com.ragqualitylab.admin.domain.AdminUser;
import com.ragqualitylab.admin.domain.AdminUserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정이 하나도 없을 때만 환경변수 값으로 초기 관리자 1명을 만든다.
 * 비밀번호는 로그에 남기지 않는다. 계정이 이미 있으면 환경변수를 무시한다.
 */
@Component
public class InitialAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuthProperties properties;
    private final Clock clock;
    private final String username;
    private final String password;

    public InitialAdminInitializer(AdminUserRepository repository, PasswordEncoder passwordEncoder,
                                   AdminAuthProperties properties, Clock clock,
                                   @Value("${ADMIN_INITIAL_USERNAME:}") String username,
                                   @Value("${ADMIN_INITIAL_PASSWORD:}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.username = username.strip();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            log.warn("관리자 계정이 없습니다. ADMIN_INITIAL_USERNAME / ADMIN_INITIAL_PASSWORD를 설정하고 재시작하세요.");
            return;
        }
        if (password.length() < properties.minPasswordLength()) {
            throw new IllegalStateException(
                "ADMIN_INITIAL_PASSWORD는 " + properties.minPasswordLength() + "자 이상이어야 합니다.");
        }
        repository.save(new AdminUser(username, passwordEncoder.encode(password), clock.instant()));
        log.info("초기 관리자 계정을 만들었습니다: {}", username);
    }
}
