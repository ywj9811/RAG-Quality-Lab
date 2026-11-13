package com.ragqualitylab.document.application;

import com.ragqualitylab.document.domain.DocumentRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 재시작 시 처리 중이던 문서를 FAILED(INTERRUPTED)로 바꿔, 관리자가 재처리할 수 있게 한다 (RK-18). */
@Component
public class InterruptedProcessingRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InterruptedProcessingRecovery.class);

    private final DocumentRepository documents;
    private final Clock clock;

    public InterruptedProcessingRecovery(DocumentRepository documents, Clock clock) {
        this.documents = documents;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int count = documents.failInterrupted(clock.instant());
        if (count > 0) {
            log.warn("중단된 문서 처리 {}건을 FAILED(INTERRUPTED)로 표시했습니다. 관리자 화면에서 재처리하세요.", count);
        }
    }
}
