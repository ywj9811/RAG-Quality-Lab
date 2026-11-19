package com.ragqualitylab.common.retention;

import com.ragqualitylab.document.infrastructure.LocalFileStorage;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보관 기간이 지난 데이터를 물리 삭제한다 (명세 9절 개인정보, DOC-05).
 * - 대화: 생성 후 90일 지난 세션 삭제 → 메시지·검색 스냅샷·평가가 함께 삭제(FK CASCADE)
 * - 삭제 문서: 삭제 후 30일 지나면 원본 파일·문서·청크 삭제. 과거 답변의 검색 스냅샷은 텍스트가 남고 참조만 NULL
 * - LLM 사용량 기록은 개인 식별 정보가 없어 남긴다
 * 매일 04:00(서버 시간대)에 실행한다.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final JdbcTemplate jdbc;
    private final LocalFileStorage storage;
    private final RetentionProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public RetentionJob(JdbcTemplate jdbc, LocalFileStorage storage, RetentionProperties properties,
                        TransactionTemplate tx, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.retention.cron:0 0 4 * * *}")
    public Result run() {
        Instant now = clock.instant();
        Integer sessions = tx.execute(s -> jdbc.update("DELETE FROM chat_sessions WHERE created_at < ?",
            Timestamp.from(now.minus(properties.chatRetention()))));

        Timestamp docCutoff = Timestamp.from(now.minus(properties.deletedDocumentRetention()));
        List<String[]> expired = jdbc.query(
            "SELECT id::text, stored_path FROM documents WHERE deleted_at IS NOT NULL AND deleted_at < ?",
            (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, docCutoff);
        int documents = 0;
        for (String[] doc : expired) {
            try {
                storage.delete(doc[1]);
            } catch (IOException e) {
                log.warn("보관 기간 지난 원본 파일 삭제 실패: document={}", doc[0]);
                continue;
            }
            Integer n = tx.execute(s -> jdbc.update("DELETE FROM documents WHERE id = ?::uuid AND deleted_at < ?", doc[0], docCutoff));
            documents += n == null ? 0 : n;
        }
        Result result = new Result(sessions == null ? 0 : sessions, documents);
        if (result.sessions() > 0 || result.documents() > 0) {
            log.info("보관 기간 정리: 대화 세션 {}건, 삭제 문서 {}건", result.sessions(), result.documents());
        }
        return result;
    }

    public record Result(int sessions, int documents) {
    }
}
