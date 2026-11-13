package com.ragqualitylab.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragqualitylab.admin.application.AdminAuthProperties;
import com.ragqualitylab.admin.application.InitialAdminInitializer;
import com.ragqualitylab.admin.domain.AdminUser;
import com.ragqualitylab.admin.domain.AdminUserRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class InitialAdminInitializerTests {

    final AdminUserRepository repository = mock(AdminUserRepository.class);
    final AdminAuthProperties properties = new AdminAuthProperties(null, null, null);

    InitialAdminInitializer initializer(String username, String password) {
        return new InitialAdminInitializer(repository, PasswordEncoderFactories.createDelegatingPasswordEncoder(),
            properties, Clock.systemUTC(), username, password);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(repository.count()).thenReturn(1L);
        initializer("admin", "long-enough-password").run(null);
        verify(repository, never()).save(any());
    }

    @Test
    void skipsWhenEnvironmentIsNotSet() {
        when(repository.count()).thenReturn(0L);
        initializer("", "").run(null);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        when(repository.count()).thenReturn(0L);
        assertThatThrownBy(() -> initializer("admin", "short").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("12자 이상");
        verify(repository, never()).save(any());
    }

    @Test
    void createsAdminWhenEmpty() {
        when(repository.count()).thenReturn(0L);
        initializer("admin", "long-enough-password").run(null);
        verify(repository).save(any(AdminUser.class));
    }
}
