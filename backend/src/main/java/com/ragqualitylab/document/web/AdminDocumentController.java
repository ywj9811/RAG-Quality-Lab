package com.ragqualitylab.document.web;

import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import com.ragqualitylab.document.application.DocumentService;
import com.ragqualitylab.document.domain.Document;
import com.ragqualitylab.document.domain.DocumentStatus;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 관리자 문서 API (docs/phase0/05_api_contract.md 3절). */
@RestController
@RequestMapping("/api/admin/documents")
public class AdminDocumentController {

    private final DocumentService service;

    public AdminDocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(required = false) UUID documentKey,
                                                   Principal principal) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "파일을 읽을 수 없습니다.");
        }
        Document doc = service.upload(file.getOriginalFilename(), content, documentKey, principal.getName());
        return ResponseEntity.accepted()
            .location(URI.create("/api/admin/documents/" + doc.getId()))
            .body(DocumentResponse.of(doc));
    }

    @GetMapping
    public List<DocumentResponse> list(@RequestParam(required = false) DocumentStatus status,
                                       @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return service.list(status, includeDeleted).stream().map(DocumentResponse::of).toList();
    }

    @GetMapping("/{id}")
    public DocumentDetailResponse detail(@PathVariable UUID id) {
        DocumentService.DocumentDetail detail = service.detail(id);
        return new DocumentDetailResponse(DocumentResponse.of(detail.document()),
            detail.versions().stream().map(DocumentResponse::of).toList());
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentResponse> reprocess(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(DocumentResponse.of(service.reprocess(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record DocumentResponse(UUID id, UUID documentKey, int version, String filename, String contentType,
                                   long sizeBytes, DocumentStatus status, String failureCode, int chunkCount,
                                   Instant uploadedAt, Instant processedAt, Instant deletedAt) {
        static DocumentResponse of(Document d) {
            return new DocumentResponse(d.getId(), d.getDocumentKey(), d.getVersion(), d.getOriginalFilename(),
                d.getContentType(), d.getSizeBytes(), d.getStatus(), d.getFailureCode(), d.getChunkCount(),
                d.getUploadedAt(), d.getProcessedAt(), d.getDeletedAt());
        }
    }

    public record DocumentDetailResponse(DocumentResponse document, List<DocumentResponse> versions) {
    }
}
