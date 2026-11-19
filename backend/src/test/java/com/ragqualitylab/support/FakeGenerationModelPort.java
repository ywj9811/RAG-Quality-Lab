package com.ragqualitylab.support;

import com.ragqualitylab.llm.generation.GenerationFailure;
import com.ragqualitylab.llm.generation.GenerationModelPort;
import com.ragqualitylab.llm.generation.GenerationRequest;
import com.ragqualitylab.llm.generation.GenerationResult;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트용 생성 모델. 다음 응답·실패를 지정하고 마지막 요청을 기록한다. 외부 호출 없음. */
public class FakeGenerationModelPort implements GenerationModelPort {

    private volatile String nextAnswer = "기본 답변입니다 [1].";
    private volatile GenerationFailure.Kind nextFailure;
    private volatile GenerationRequest lastRequest;
    private final AtomicInteger calls = new AtomicInteger();

    public void answerWith(String answer) {
        this.nextAnswer = answer;
        this.nextFailure = null;
    }

    public void failWith(GenerationFailure.Kind kind) {
        this.nextFailure = kind;
    }

    public GenerationRequest lastRequest() {
        return lastRequest;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        nextAnswer = "기본 답변입니다 [1].";
        nextFailure = null;
        lastRequest = null;
        calls.set(0);
    }

    @Override
    public GenerationResult generate(GenerationRequest request) {
        calls.incrementAndGet();
        lastRequest = request;
        if (nextFailure != null) {
            throw new GenerationFailure(nextFailure, "fake", new RuntimeException("fake"));
        }
        return new GenerationResult(nextAnswer, "fake-model", 100, 20);
    }

    @Override
    public String provider() {
        return "fake";
    }

    @Override
    public String modelId() {
        return "fake-model";
    }
}
