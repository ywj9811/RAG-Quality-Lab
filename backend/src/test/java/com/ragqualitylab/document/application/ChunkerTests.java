package com.ragqualitylab.document.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragqualitylab.document.domain.ChunkDraft;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTests {

    final Chunker chunker = new Chunker(200, 30);

    static ExtractedText text(String s) {
        return new ExtractedText(List.of(new ExtractedText.Page(null, s)));
    }

    @Test
    void markdownSplitsByHeadingsAndUsesFrontMatterTitle() {
        String md = """
            ---
            id: doc-04
            title: 트랜잭션, 전파, 롤백
            ---

            # 트랜잭션, 전파, 롤백

            ## 핵심 개념

            트랜잭션은 작업 단위다.

            ## 전파 속성

            REQUIRED가 기본값이다.
            """;
        List<ChunkDraft> chunks = chunker.chunk(text(md), ContentKind.MARKDOWN, "fallback");

        assertThat(chunks).extracting(c -> c.locator().heading()).containsExactly("핵심 개념", "전파 속성");
        assertThat(chunks.get(1).content()).isEqualTo("트랜잭션, 전파, 롤백 > 전파 속성\nREQUIRED가 기본값이다.");
        assertThat(chunks).noneMatch(c -> c.content().contains("id: doc-04"));
    }

    @Test
    void locatorPointsToOriginalText() {
        String md = "# 제목\n\n## 소제목\n\n본문 문장입니다.\n";
        ChunkDraft chunk = chunker.chunk(text(md), ContentKind.MARKDOWN, "x").getFirst();
        assertThat(md.substring(chunk.locator().charStart(), chunk.locator().charEnd())).isEqualTo("본문 문장입니다.");
    }

    @Test
    void headingsInsideCodeFencesAreIgnored() {
        String md = "# 문서\n\n## 예시\n\n```bash\n# 주석은 제목이 아니다\necho hi\n```\n";
        List<ChunkDraft> chunks = chunker.chunk(text(md), ContentKind.MARKDOWN, "x");
        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.locator().heading()).isEqualTo("예시");
            assertThat(c.content()).contains("# 주석은 제목이 아니다");
        });
    }

    @Test
    void longSectionsAreSplitWithinSizeAndOverlap() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            body.append("문장 ").append(i).append("번은 청크 분할을 시험하기 위한 문장입니다. ");
        }
        String plain = body.toString();
        List<ChunkDraft> chunks = chunker.chunk(text(plain), ContentKind.TEXT, "제목");

        assertThat(chunks).hasSizeGreaterThan(3);
        for (ChunkDraft c : chunks) {
            assertThat(c.locator().charEnd() - c.locator().charStart()).isLessThanOrEqualTo(200);
        }
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).locator().charStart()).isLessThan(chunks.get(i - 1).locator().charEnd());
        }
        // 마지막 청크는 원문 끝까지 포함한다
        assertThat(chunks.getLast().locator().charEnd()).isEqualTo(plain.strip().length());
        assertThat(chunks).extracting(ChunkDraft::index).containsExactly(
            java.util.stream.IntStream.range(0, chunks.size()).boxed().toArray(Integer[]::new));
    }

    @Test
    void pdfPagesKeepPageNumbers() {
        var pages = new ExtractedText(List.of(
            new ExtractedText.Page(1, "첫 페이지 내용입니다."),
            new ExtractedText.Page(2, "둘째 페이지 내용입니다.")));
        List<ChunkDraft> chunks = chunker.chunk(pages, ContentKind.PDF, "보고서");
        assertThat(chunks).extracting(c -> c.locator().page()).containsExactly(1, 2);
        assertThat(chunks.getFirst().content()).startsWith("보고서\n");
    }

    @Test
    void blankSectionsProduceNoChunks() {
        assertThat(chunker.chunk(text("# 제목\n\n## 빈 소제목\n\n   \n"), ContentKind.MARKDOWN, "x")).isEmpty();
    }
}
