package com.ragqualitylab.support;

import com.ragqualitylab.llm.generation.GenerationFailure;
import com.ragqualitylab.llm.judge.JudgeModelPort;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트용 Judge. 응답·실패·지연을 지정하고 마지막 프롬프트를 기록한다. */
public class FakeJudgeModelPort implements JudgeModelPort {

    public static final String VALID = """
        {"scores":{"faithfulness":{"score":30,"reason":"근거에 부합"},"relevance":{"score":23,"reason":"질문에 답함"},
        "completeness":{"score":15,"reason":"조건 일부 누락"},"citationAccuracy":{"score":15,"reason":"인용 일부 부정확"}},
        "total":99,"issues":["설명 누락"],"suggestions":["조건 추가"]}""";

    private volatile String next = VALID;
    private volatile GenerationFailure.Kind failure;
    private volatile CountDownLatch gate;
    private volatile String lastUserPrompt;
    private final AtomicInteger calls = new AtomicInteger();

    public void respond(String json) { this.next = json; this.failure = null; }
    public void fail(GenerationFailure.Kind kind) { this.failure = kind; }
    /** 다음 호출을 release() 때까지 멈춘다 (진행 중 중복 요청 테스트용). */
    public void hold() { this.gate = new CountDownLatch(1); }
    public void release() { if (gate != null) gate.countDown(); }
    public String lastUserPrompt() { return lastUserPrompt; }
    public int calls() { return calls.get(); }

    public void reset() {
        next = VALID;
        failure = null;
        release();
        gate = null;
        lastUserPrompt = null;
        calls.set(0);
    }

    @Override
    public JudgeResponse judge(String systemPrompt, String userPrompt) {
        calls.incrementAndGet();
        lastUserPrompt = userPrompt;
        CountDownLatch g = gate;
        if (g != null) {
            try {
                g.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (failure != null) {
            throw new GenerationFailure(failure, "fake", new RuntimeException());
        }
        return new JudgeResponse(next, "fake-judge", 500, 120);
    }

    @Override public String provider() { return "fake"; }
    @Override public String modelId() { return "fake-judge"; }
}
