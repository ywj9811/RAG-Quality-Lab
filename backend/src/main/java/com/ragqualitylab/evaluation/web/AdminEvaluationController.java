package com.ragqualitylab.evaluation.web;

import com.ragqualitylab.common.web.ProblemDetails;
import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.evaluation.application.EvaluationService;
import com.ragqualitylab.evaluation.domain.Evaluation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 평가 API (05_api_contract 5절). */
@RestController
public class AdminEvaluationController {

    static final String DISCLAIMER = "이 점수는 LLM이 추정한 품질 지표이며 실제 정답률이 아닙니다. 근거 문서 자체가 틀린 경우는 판별하지 못합니다.";

    private final EvaluationService service;
    private final com.ragqualitylab.evaluation.application.Rubric rubric;

    public AdminEvaluationController(EvaluationService service, com.ragqualitylab.evaluation.application.Rubric rubric) {
        this.service = service;
        this.rubric = rubric;
    }

    @PostMapping("/api/admin/evaluations")
    public ResponseEntity<Map<String, Object>> request(@Valid @RequestBody EvaluationRequest body, Principal principal) {
        Evaluation e = service.request(body.messageId(), principal.getName());
        return ResponseEntity.accepted().location(URI.create("/api/admin/evaluations/" + e.getId()))
            .body(Map.of("evaluationId", e.getId(), "status", e.getStatus().name()));
    }

    @GetMapping("/api/admin/evaluations/{id}")
    public EvaluationResponse get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    @GetMapping("/api/admin/messages/{id}/evaluations")
    public List<EvaluationResponse> history(@PathVariable UUID id) {
        return service.history(id).stream().map(this::toResponse).toList();
    }

    @ExceptionHandler(EvaluationService.EvaluationInProgress.class)
    ResponseEntity<ProblemDetail> inProgress(EvaluationService.EvaluationInProgress e) {
        ProblemDetail p = ProblemDetails.of(ErrorCode.EVALUATION_IN_PROGRESS, e.getMessage());
        p.setProperty("existingEvaluationId", e.existingEvaluationId());
        return ResponseEntity.status(ErrorCode.EVALUATION_IN_PROGRESS.status()).body(p);
    }

    private EvaluationResponse toResponse(EvaluationService.EvaluationView v) {
        Evaluation e = v.evaluation();
        Map<String, String> labels = new java.util.HashMap<>();
        rubric.criteria().forEach(c -> labels.put(c.key(), c.label()));
        return new EvaluationResponse(e.getId(), e.getAnswerMessageId(), e.getStatus().name(), e.getRubricVersion(),
            e.getJudgeModelId(), e.getPromptVersion(), e.getTotalScore(),
            v.scores().stream().map(s -> new ScoreResponse(s.criterion(), labels.getOrDefault(s.criterion(), s.criterion()),
                s.score(), s.maxScore(), s.reason())).toList(),
            v.issues(), v.suggestions(), DISCLAIMER, e.getCreatedAt(), e.getCompletedAt(), e.getErrorCode());
    }

    public record EvaluationRequest(@NotNull UUID messageId) {
    }

    public record ScoreResponse(String criterion, String label, int score, int maxScore, String reason) {
    }

    public record EvaluationResponse(UUID evaluationId, UUID messageId, String status, String rubricVersion,
                                     String judgeModelId, String promptVersion, Integer totalScore,
                                     List<ScoreResponse> scores, List<String> issues, List<String> suggestions,
                                     String disclaimer, Instant createdAt, Instant completedAt, String errorCode) {
    }
}
