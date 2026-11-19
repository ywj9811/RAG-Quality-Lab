package com.ragqualitylab.document.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 문서 처리 전용 실행기. 로컬 Ollama 부하를 고려해 기본 1개 스레드로 순차 처리한다. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class IngestionAsyncConfig {

    @Bean
    ThreadPoolTaskExecutor ingestionExecutor(@Value("${app.document.processing-threads:1}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
