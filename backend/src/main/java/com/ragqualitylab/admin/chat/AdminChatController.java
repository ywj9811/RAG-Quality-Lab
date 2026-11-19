package com.ragqualitylab.admin.chat;

import com.ragqualitylab.common.web.PageResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 대화 조회 API (05_api_contract 4절). from·to는 한국 시간 날짜(yyyy-MM-dd), to는 그날 끝까지 포함. */
@RestController
@RequestMapping("/api/admin/chats")
public class AdminChatController {

    private final AdminChatQueryService service;

    public AdminChatController(AdminChatQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminChatQueryService.SessionSummary> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(from, to, status, page, size);
    }

    @GetMapping("/{id}")
    public AdminChatQueryService.SessionDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }
}
