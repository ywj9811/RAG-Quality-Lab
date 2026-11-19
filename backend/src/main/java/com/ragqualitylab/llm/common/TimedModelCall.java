package com.ragqualitylab.llm.common;

import com.ragqualitylab.llm.generation.GenerationFailure;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** 외부 모델 호출에 전체 대기 시간 상한을 두고, 실패를 RATE_LIMITED·TIMEOUT·UNAVAILABLE로 분류한다. 생성·Judge 공용. */
public final class TimedModelCall {

    private TimedModelCall() {
    }

    public static <T> T call(ExecutorService executor, Duration timeout, Supplier<T> supplier) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier, executor);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new GenerationFailure(GenerationFailure.Kind.TIMEOUT, "모델 응답 시간 초과", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationFailure(GenerationFailure.Kind.UNAVAILABLE, "모델 호출 중단", e);
        } catch (ExecutionException e) {
            throw classify(e.getCause());
        }
    }

    public static GenerationFailure classify(Throwable cause) {
        String message = String.valueOf(cause == null ? null : cause.getMessage());
        boolean rateLimited = message.contains("429") || message.contains("RESOURCE_EXHAUSTED")
            || message.toLowerCase().contains("quota");
        return new GenerationFailure(rateLimited ? GenerationFailure.Kind.RATE_LIMITED : GenerationFailure.Kind.UNAVAILABLE,
            "모델 호출 실패", cause);
    }
}
