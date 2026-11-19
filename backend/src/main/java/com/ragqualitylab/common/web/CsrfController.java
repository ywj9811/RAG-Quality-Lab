package com.ragqualitylab.common.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** XSRF-TOKEN 쿠키 발급. 관리자·방문자 모두 상태 변경 요청에 X-XSRF-TOKEN 헤더를 보낸다. */
@RestController
public class CsrfController {

    @GetMapping("/api/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }
}
