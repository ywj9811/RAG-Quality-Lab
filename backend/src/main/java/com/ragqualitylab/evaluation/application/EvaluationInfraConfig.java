package com.ragqualitylab.evaluation.application;

import com.ragqualitylab.evaluation.domain.EvaluationRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

/** 평가 전용 실행기(1스레드, Judge RPM 5 고려)와 재시작 복구 (RK-18). */
@Configuration(proxyBeanMethods = false)
public class EvaluationInfraConfig {

    private static final Logger log = LoggerFactory.getLogger(EvaluationInfraConfig.class);

    @Bean
    ThreadPoolTaskExecutor evaluationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("judge-");
        executor.initialize();
        return executor;
    }

    @Bean
    ApplicationRunner interruptedEvaluationRecovery(EvaluationRepository evaluations, TransactionTemplate tx, Clock clock) {
        return args -> {
            Integer count = tx.execute(status -> evaluations.failInterrupted(clock.instant()));
            if (count != null && count > 0) {
                log.warn("중단된 평가 {}건을 FAILED(INTERRUPTED)로 표시했습니다. 재평가하세요.", count);
            }
        };
    }
}
