package com.ragqualitylab.common.web;

import java.util.List;

/** 목록 응답 공통 형식 (docs/phase0/05_api_contract.md 0절). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements) {

    public static final int MAX_SIZE = 100;
}
