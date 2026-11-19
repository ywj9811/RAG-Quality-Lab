package com.ragqualitylab.evaluation.application;

import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.evaluation.domain.Evaluation;
import com.ragqualitylab.evaluation.domain.EvaluationRepository;
import com.ragqualitylab.evaluation.domain.EvaluationScoreRepository;
import com.ragqualitylab.evaluation.domain.EvaluationStatus;
import com.ragqualitylab.llm.common.CauseSummary;
import com.ragqualitylab.llm.generation.GenerationFailure;
import com.ragqualitylab.llm.judge.JudgeModelPort;
import com.ragqualitylab.llm.usage.LlmBudget;
import com.ragqualitylab.llm.usage.OperationType;
import com.ragqualitylab.llm.usage.UsageStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 평가 실행 (EVAL-02~05). PENDING → RUNNING → COMPLETED | FAILED.
 * Judge 호출은 트랜잭션 밖. 응답 JSON을 검증하고 총점은 서버에서 계산한다.
 * 무료 RPD가 작아(20) 재시도하지 않는다. 실패하면 error_code를 남기고 관리자가 재평가한다.
 */
@Component
public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);
    static final int MAX_RAW_RESPONSE_CHARS = 20_000;

    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final JudgeInputLoader inputs;
    private final JudgePromptBuilder prompts;
    private final Rubric rubric;
    private final ObjectProvider<JudgeModelPort> judge;
    private final LlmBudget budget;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;

    public EvaluationRunner(EvaluationRepository evaluations, EvaluationScoreRepository scores, JudgeInputLoader inputs,
                            JudgePromptBuilder prompts, Rubric rubric, ObjectProvider<JudgeModelPort> judge,
                            LlmBudget budget, TransactionTemplate tx, JsonMapper json, Clock clock) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.inputs = inputs;
        this.prompts = prompts;
        this.rubric = rubric;
        this.judge = judge;
        this.budget = budget;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    @Async("evaluationExecutor")
    @TransactionalEventListener
    public void onRequested(EvaluationRequestedEvent event) {
        run(event.evaluationId());
    }

    public void run(UUID evaluationId) {
        JudgeInput input = tx.execute(status -> {
            if (evaluations.claim(evaluationId) != 1) {
                return null;
            }
            Evaluation e = evaluations.findById(evaluationId).orElseThrow();
            JudgeInput loaded = inputs.load(e.getAnswerMessageId());
            e.start(json.writeValueAsString(loaded), clock.instant());
            return loaded;
        });
        if (input == null) {
            return;
        }
        JudgeModelPort model = judge.getIfAvailable();
        if (model == null) {
            fail(evaluationId, "UPSTREAM_UNAVAILABLE", null);
            return;
        }
        UUID reservation;
        try {
            reservation = budget.reserve(OperationType.JUDGE, model.provider(), model.modelId());
        } catch (ApiException e) {
            fail(evaluationId, e.code().name(), null);
            return;
        }

        long started = System.nanoTime();
        JudgeModelPort.JudgeResponse response;
        try {
            response = model.judge(prompts.systemPrompt(), prompts.userPrompt(input, rubric));
        } catch (GenerationFailure e) {
            int ms = millisSince(started);
            String code = switch (e.kind()) {
                case RATE_LIMITED -> "UPSTREAM_RATE_LIMITED";
                case TIMEOUT -> "UPSTREAM_TIMEOUT";
                case UNAVAILABLE -> "UPSTREAM_UNAVAILABLE";
            };
            budget.complete(reservation, e.kind() == GenerationFailure.Kind.RATE_LIMITED ? UsageStatus.RATE_LIMITED
                : e.kind() == GenerationFailure.Kind.TIMEOUT ? UsageStatus.TIMEOUT : UsageStatus.ERROR,
                null, null, ms, null, evaluationId);
            log.warn("평가 실패: id={} kind={} cause={}", evaluationId, e.kind(), CauseSummary.of(e.getCause()));
            fail(evaluationId, code, null);
            return;
        } catch (RuntimeException e) {
            budget.complete(reservation, UsageStatus.ERROR, null, null, millisSince(started), null, evaluationId);
            log.error("평가 중 예상하지 못한 오류: id={}", evaluationId, e);
            fail(evaluationId, "INTERNAL_ERROR", null);
            return;
        }
        int ms = millisSince(started);
        budget.complete(reservation, UsageStatus.SUCCESS, response.inputTokens(), response.outputTokens(), ms, null,
            evaluationId);

        String raw = truncate(response.text());
        JudgeOutputParser.Parsed parsed;
        try {
            parsed = JudgeOutputParser.parse(response.text(), rubric, json);
        } catch (JudgeOutputParser.InvalidJudgeOutput e) {
            log.warn("평가 응답 검증 실패: id={} reason={}", evaluationId, e.getMessage());
            fail(evaluationId, "INVALID_JUDGE_OUTPUT", raw);
            return;
        }
        tx.executeWithoutResult(status -> {
            Evaluation e = evaluations.findById(evaluationId).orElseThrow();
            if (e.getStatus() != EvaluationStatus.RUNNING) {
                return;
            }
            scores.saveAll(evaluationId, parsed.scores());
            e.complete(parsed.total(), json.writeValueAsString(parsed.issues()),
                json.writeValueAsString(parsed.suggestions()), raw, response.modelId(), clock.instant());
        });
    }

    private void fail(UUID evaluationId, String code, String raw) {
        tx.executeWithoutResult(status -> evaluations.findById(evaluationId)
            .filter(e -> e.getStatus() == EvaluationStatus.RUNNING)
            .ifPresent(e -> e.fail(code, raw, clock.instant())));
    }

    private static String truncate(String s) {
        return s == null || s.length() <= MAX_RAW_RESPONSE_CHARS ? s : s.substring(0, MAX_RAW_RESPONSE_CHARS);
    }

    private static int millisSince(long nanoStart) {
        return (int) Duration.ofNanos(System.nanoTime() - nanoStart).toMillis();
    }
}
