package com.ragqualitylab.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ragqualitylab.TestcontainersConfiguration;
import com.ragqualitylab.document.application.InterruptedProcessingRecovery;
import com.ragqualitylab.document.domain.DocumentRepository;
import com.ragqualitylab.document.domain.DocumentStatus;
import com.ragqualitylab.retrieval.application.RetrievalService;
import com.ragqualitylab.retrieval.application.RetrievedChunk;
import com.ragqualitylab.support.FakeEmbeddingPort;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentIngestionIntegrationTests.FakeEmbeddingConfig.class})
class DocumentIngestionIntegrationTests {

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.document.upload-dir", () -> uploadDir.toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeEmbeddingConfig {
        @Bean
        @Primary
        FakeEmbeddingPort fakeEmbeddingPort() {
            return new FakeEmbeddingPort();
        }
    }

    static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    static final String TX_DOC = """
        ---
        title: 트랜잭션 테스트 문서
        ---
        # 트랜잭션 테스트 문서

        ## 전파 속성

        기본 전파 속성은 REQUIRED 이며 기존 트랜잭션에 참여한다.

        ## 롤백 규칙

        언체크 예외가 발생하면 롤백하고 체크 예외는 커밋한다.
        """;

    @Autowired MockMvc mvc;
    @Autowired DocumentRepository documents;
    @Autowired RetrievalService retrieval;
    @Autowired FakeEmbeddingPort fakeEmbedding;
    @Autowired JdbcTemplate jdbc;
    @Autowired InterruptedProcessingRecovery recovery;

    @BeforeEach
    @AfterEach
    void clean() {
        fakeEmbedding.setFailing(false);
        jdbc.update("DELETE FROM documents");
    }

    @Test
    void markdownUploadBecomesReadyAndSearchable() throws Exception {
        UUID id = upload("tx.md", TX_DOC);
        awaitStatus(id, DocumentStatus.READY);

        mvc.perform(get("/api/admin/documents/" + id).with(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.document.chunkCount").value(2))
            .andExpect(jsonPath("$.document.version").value(1));

        List<RetrievedChunk> hits = retrieval.search("기본 전파 속성은 무엇인가", 2);
        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().locator().heading()).isEqualTo("전파 속성");
        assertThat(hits.getFirst().content()).startsWith("트랜잭션 테스트 문서 > 전파 속성\n");
        assertThat(hits.getFirst().rank()).isEqualTo(1);
        String model = jdbc.queryForObject("SELECT DISTINCT embedding_model FROM document_chunks", String.class);
        assertThat(model).isEqualTo("fake-embedding");
    }

    @Test
    void deletedDocumentIsExcludedFromSearchImmediately() throws Exception {
        UUID id = upload("tx.md", TX_DOC);
        awaitStatus(id, DocumentStatus.READY);
        assertThat(retrieval.search("전파 속성", 5)).isNotEmpty();

        mvc.perform(delete("/api/admin/documents/" + id).with(ADMIN).with(csrf())).andExpect(status().isNoContent());

        assertThat(retrieval.search("전파 속성", 5)).isEmpty();
        mvc.perform(get("/api/admin/documents").with(ADMIN)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/documents?includeDeleted=true").with(ADMIN))
            .andExpect(jsonPath("$[0].deletedAt").isNotEmpty());
        mvc.perform(delete("/api/admin/documents/" + id).with(ADMIN).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void newVersionReplacesPreviousVersionWhenReady() throws Exception {
        UUID v1 = upload("tx.md", TX_DOC);
        awaitStatus(v1, DocumentStatus.READY);
        UUID key = documents.findById(v1).orElseThrow().getDocumentKey();

        String body = mvc.perform(multipart("/api/admin/documents").file(file("tx.md", TX_DOC + "\n## 추가\n\n격리 수준 설명.\n"))
                .param("documentKey", key.toString()).with(ADMIN).with(csrf()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.version").value(2))
            .andReturn().getResponse().getContentAsString();
        UUID v2 = UUID.fromString(JsonPath.read(body, "$.id"));
        awaitStatus(v2, DocumentStatus.READY);

        assertThat(documents.findById(v1).orElseThrow().isDeleted()).isTrue();
        assertThat(retrieval.search("전파 속성 REQUIRED", 10))
            .extracting(RetrievedChunk::documentId).containsOnly(v2);
    }

    @Test
    void duplicateContentIsRejected() throws Exception {
        UUID id = upload("tx.md", TX_DOC);
        awaitStatus(id, DocumentStatus.READY);
        mvc.perform(multipart("/api/admin/documents").file(file("copy.md", TX_DOC)).with(ADMIN).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_DOCUMENT"));
    }

    @Test
    void invalidUploadsAreRejected() throws Exception {
        mvc.perform(multipart("/api/admin/documents").file(file("a.docx", "x")).with(ADMIN).with(csrf()))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(multipart("/api/admin/documents").file(file("fake.pdf", "not a pdf")).with(ADMIN).with(csrf()))
            .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/admin/documents").file(new MockMultipartFile("file", "big.md", "text/markdown",
                new byte[10 * 1024 * 1024 + 1])).with(ADMIN).with(csrf()))
            .andExpect(status().isContentTooLarge())
            .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mvc.perform(multipart("/api/admin/documents").with(ADMIN).with(csrf()))
            .andExpect(status().isBadRequest());
        assertThat(documents.count()).isZero();
    }

    @Test
    void scannedPdfWithoutTextFails() throws Exception {
        UUID id = uploadBytes("scan.pdf", pdf(null));
        awaitStatus(id, DocumentStatus.FAILED);
        assertThat(documents.findById(id).orElseThrow().getFailureCode()).isEqualTo("NO_TEXT");
    }

    @Test
    void pdfWithTextKeepsPageNumbers() throws Exception {
        UUID id = uploadBytes("report.pdf", pdf("Transaction propagation REQUIRED joins the existing transaction."));
        awaitStatus(id, DocumentStatus.READY);
        assertThat(retrieval.search("propagation REQUIRED", 1).getFirst().locator().page()).isEqualTo(1);
    }

    @Test
    void embeddingFailureCanBeReprocessed() throws Exception {
        fakeEmbedding.setFailing(true);
        UUID id = upload("tx.md", TX_DOC);
        awaitStatus(id, DocumentStatus.FAILED);
        assertThat(documents.findById(id).orElseThrow().getFailureCode()).isEqualTo("EMBEDDING_UNAVAILABLE");

        fakeEmbedding.setFailing(false);
        mvc.perform(post("/api/admin/documents/" + id + "/reprocess").with(ADMIN).with(csrf()))
            .andExpect(status().isAccepted());
        awaitStatus(id, DocumentStatus.READY);

        mvc.perform(post("/api/admin/documents/" + id + "/reprocess").with(ADMIN).with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test
    void interruptedProcessingIsMarkedFailedOnStartup() throws Exception {
        UUID id = upload("tx.md", TX_DOC);
        awaitStatus(id, DocumentStatus.READY);
        jdbc.update("UPDATE documents SET status = 'PROCESSING' WHERE id = ?", id);

        recovery.run(null);

        assertThat(documents.findById(id).orElseThrow().getFailureCode()).isEqualTo("INTERRUPTED");
    }

    @Test
    void endpointsRequireAdmin() throws Exception {
        mvc.perform(get("/api/admin/documents")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/documents").file(file("a.md", "# x")).with(csrf()))
            .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/documents").file(file("a.md", "# x")).with(ADMIN))
            .andExpect(status().isForbidden());
    }

    // --- helpers ---

    static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8));
    }

    UUID upload(String name, String content) throws Exception {
        return uploadBytes(name, content.getBytes(StandardCharsets.UTF_8));
    }

    UUID uploadBytes(String name, byte[] content) throws Exception {
        String body = mvc.perform(multipart("/api/admin/documents")
                .file(new MockMultipartFile("file", name, "application/octet-stream", content)).with(ADMIN).with(csrf()))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("UPLOADED"))
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    void awaitStatus(UUID id, DocumentStatus expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        DocumentStatus current = null;
        while (System.currentTimeMillis() < deadline) {
            current = documents.findById(id).orElseThrow().getStatus();
            if (current == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("status " + current + " != " + expected);
    }

    static byte[] pdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null) {
                try (var stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text);
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
