package com.ragqualitylab.evaluation.application;

import com.ragqualitylab.admin.domain.AdminUser;
import com.ragqualitylab.admin.domain.AdminUserRepository;
import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.evaluation.domain.Evaluation;
import com.ragqualitylab.evaluation.domain.EvaluationRepository;
import com.ragqualitylab.evaluation.domain.EvaluationScoreRepository;
import com.ragqualitylab.llm.judge.JudgeModelPort;
import com.ragqualitylab.llm.judge.JudgeProperties;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 수동 평가 요청·조회 (ADM-04~06, EVAL-01·05). 일반 채팅 경로에서는 평가하지 않는다.
 * 같은 답변에 PENDING·RUNNING 평가가 있으면 409(기존 평가 ID 포함). 끝난 뒤에는 재평가를 새 행으로 남긴다.
 */
@Service
public class EvaluationService {

    private final EvaluationRepository evaluations;
    private final EvaluationScoreRepository scores;
    private final JudgeInputLoader inputs;
    private final Rubric rubric;
    private final JudgePromptBuilder prompts;
    private final JudgeProperties judgeProperties;
    private final ObjectProvider<JudgeModelPort> judge;
    private final AdminUserRepository admins;
    private final ApplicationEventPublisher events;
    private final JsonMapper json;
    private final Clock clock;

    public EvaluationService(EvaluationRepository evaluations, EvaluationScoreRepository scores,
                             JudgeInputLoader inputs, Rubric rubric, JudgePromptBuilder prompts,
                             JudgeProperties judgeProperties, ObjectProvider<JudgeModelPort> judge,
                             AdminUserRepository admins, ApplicationEventPublisher events, JsonMapper json, Clock clock) {
        this.evaluations = evaluations;
        this.scores = scores;
        this.inputs = inputs;
        this.rubric = rubric;
        this.prompts = prompts;
        this.judgeProperties = judgeProperties;
        this.judge = judge;
        this.admins = admins;
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Evaluation request(UUID messageId, String adminUsername) {
        inputs.requireEvaluable(messageId);
        if (judge.getIfAvailable() == null) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "평가 모델이 설정되지 않았습니다.");
        }
        evaluations.findInProgress(messageId).ifPresent(existing -> {
            throw new EvaluationInProgress(existing.getId());
        });
        UUID requestedBy = admins.findByUsername(adminUsername).map(AdminUser::getId).orElse(null);
        Evaluation saved;
        try {
            saved = evaluations.saveAndFlush(new Evaluation(messageId, rubric.version(), judgeProperties.model(),
                prompts.version(), requestedBy, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청: 부분 유니크 인덱스가 막았다
            throw new EvaluationInProgress(evaluations.findInProgress(messageId).map(Evaluation::getId).orElse(null));
        }
        events.publishEvent(new EvaluationRequestedEvent(saved.getId()));
        return saved;
    }

    @Transactional(readOnly = true)
    public EvaluationView get(UUID id) {
        return view(evaluations.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "평가를 찾을 수 없습니다.")));
    }

    @Transactional(readOnly = true)
    public List<EvaluationView> history(UUID messageId) {
        return evaluations.findByAnswerMessageIdOrderByCreatedAtDesc(messageId).stream().map(this::view).toList();
    }

    private EvaluationView view(Evaluation e) {
        List<EvaluationScoreRepository.CriterionScore> list =
            EvaluationScoreRepository.ordered(scores.find(e.getId()), rubric.keys());
        return new EvaluationView(e, list, readList(e.getIssues()), readList(e.getSuggestions()));
    }

    private List<String> readList(String jsonArray) {
        if (jsonArray == null) {
            return List.of();
        }
        return List.of(json.readValue(jsonArray, String[].class));
    }

    public record EvaluationView(Evaluation evaluation, List<EvaluationScoreRepository.CriterionScore> scores,
                                 List<String> issues, List<String> suggestions) {
    }

    /** 진행 중 평가가 있어 거절. 응답에 기존 평가 ID를 담는다. */
    public static class EvaluationInProgress extends ApiException {
        private final UUID existingEvaluationId;

        public EvaluationInProgress(UUID existingEvaluationId) {
            super(ErrorCode.EVALUATION_IN_PROGRESS, "이 답변은 이미 평가 중입니다.");
            this.existingEvaluationId = existingEvaluationId;
        }

        public UUID existingEvaluationId() {
            return existingEvaluationId;
        }
    }
}
