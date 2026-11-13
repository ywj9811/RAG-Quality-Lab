package com.ragqualitylab.document.application;

import com.ragqualitylab.document.domain.ChunkDraft;
import com.ragqualitylab.document.domain.SourceLocator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 청크 분할 (DOC-04, 03_architecture 5절).
 * - 마크다운: 앞머리(front matter)를 건너뛰고, 제목(#~###) 경계로 구역을 나눈 뒤 구역 안에서 길이 기준으로 자른다.
 *   코드 블록(```) 안의 # 줄은 제목으로 보지 않는다.
 * - 텍스트·PDF: 페이지(또는 전체)를 한 구역으로 보고 길이 기준으로 자른다.
 * - 자르는 위치는 문단(빈 줄) → 줄바꿈 → 문장 끝 순으로 찾고, 앞 청크와 overlap만큼 겹친다.
 * - 청크 content 앞에 "문서 제목 > 소제목"을 붙여 검색 시 맥락을 준다. 위치(charStart/End)는 원문 기준이다.
 */
@Component
public class Chunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FRONT_MATTER_TITLE = Pattern.compile("(?m)^title:\\s*(.+?)\\s*$");

    private final int size;
    private final int overlap;

    @Autowired
    public Chunker(DocumentProperties properties) {
        this(properties.chunkSizeChars(), properties.chunkOverlapChars());
    }

    Chunker(int size, int overlap) {
        this.size = size;
        this.overlap = overlap;
    }

    public List<ChunkDraft> chunk(ExtractedText extracted, ContentKind kind, String fallbackTitle) {
        List<ChunkDraft> chunks = new ArrayList<>();
        for (ExtractedText.Page page : extracted.pages()) {
            List<Section> sections = kind == ContentKind.MARKDOWN
                ? markdownSections(page.text(), fallbackTitle)
                : List.of(new Section(fallbackTitle, null, 0, page.text().length()));
            for (Section section : sections) {
                for (int[] range : split(page.text(), section.start, section.end)) {
                    String body = page.text().substring(range[0], range[1]);
                    String header = section.heading == null || section.heading.equals(section.title)
                        ? section.title
                        : section.title + " > " + section.heading;
                    chunks.add(new ChunkDraft(chunks.size(), header + "\n" + body,
                        new SourceLocator(section.heading, page.pageNumber(), range[0], range[1])));
                }
            }
        }
        return chunks;
    }

    /** 제목 경계로 구역을 나눈다. 각 구역 본문은 제목 줄을 제외한 범위다. */
    List<Section> markdownSections(String text, String fallbackTitle) {
        int bodyStart = frontMatterEnd(text);
        String title = frontMatterTitle(text, bodyStart).orElse(null);

        List<int[]> headingLines = new ArrayList<>();   // {lineStart, lineEnd, level}
        List<String> headingTexts = new ArrayList<>();
        boolean inFence = false;
        int pos = bodyStart;
        while (pos < text.length()) {
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String line = text.substring(pos, lineEnd);
            if (line.stripLeading().startsWith("```")) {
                inFence = !inFence;
            } else if (!inFence) {
                Matcher m = HEADING.matcher(line);
                if (m.matches()) {
                    headingLines.add(new int[] {pos, lineEnd, m.group(1).length()});
                    headingTexts.add(m.group(2).strip());
                    if (title == null && m.group(1).length() == 1) {
                        title = m.group(2).strip();
                    }
                }
            }
            pos = lineEnd + 1;
        }
        if (title == null) {
            title = fallbackTitle;
        }

        List<Section> sections = new ArrayList<>();
        int sectionStart = bodyStart;
        String currentHeading = null;
        for (int i = 0; i < headingLines.size(); i++) {
            int[] h = headingLines.get(i);
            sections.add(new Section(title, currentHeading, sectionStart, h[0]));
            // 최상위 제목(#)은 문서 제목이므로 그 아래 본문은 소제목 없이 둔다
            currentHeading = h[2] == 1 ? null : headingTexts.get(i);
            sectionStart = Math.min(h[1] + 1, text.length());
        }
        sections.add(new Section(title, currentHeading, sectionStart, text.length()));
        sections.removeIf(s -> text.substring(s.start, s.end).isBlank());
        return sections;
    }

    /** [start, end) 범위를 길이 기준으로 자른 범위 목록. 앞뒤 공백은 범위에서 제외한다. */
    List<int[]> split(String text, int start, int end) {
        List<int[]> ranges = new ArrayList<>();
        int pos = start;
        while (pos < end) {
            int hardEnd = Math.min(pos + size, end);
            int cut = hardEnd == end ? end : findBreak(text, pos + size / 2, hardEnd);
            int[] trimmed = trim(text, pos, cut);
            if (trimmed[0] < trimmed[1]) {
                ranges.add(trimmed);
            }
            if (cut >= end) {
                break;
            }
            int next = cut - overlap;
            pos = next > pos ? next : cut;
        }
        return ranges;
    }

    private static int findBreak(String text, int min, int max) {
        int paragraph = text.lastIndexOf("\n\n", max - 1);
        if (paragraph >= min) {
            return paragraph + 2;
        }
        int newline = text.lastIndexOf('\n', max - 1);
        if (newline >= min) {
            return newline + 1;
        }
        for (int i = max - 1; i >= min; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '?' || c == '!') && i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        int space = text.lastIndexOf(' ', max - 1);
        return space >= min ? space + 1 : max;
    }

    private static int[] trim(String text, int start, int end) {
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }

    private static int frontMatterEnd(String text) {
        if (!text.startsWith("---\n")) {
            return 0;
        }
        int close = text.indexOf("\n---", 4);
        if (close < 0) {
            return 0;
        }
        int lineEnd = text.indexOf('\n', close + 4);
        return lineEnd < 0 ? text.length() : lineEnd + 1;
    }

    private static java.util.Optional<String> frontMatterTitle(String text, int frontMatterEnd) {
        if (frontMatterEnd == 0) {
            return java.util.Optional.empty();
        }
        Matcher m = FRONT_MATTER_TITLE.matcher(text.substring(0, frontMatterEnd));
        return m.find() ? java.util.Optional.of(m.group(1)) : java.util.Optional.empty();
    }

    record Section(String title, String heading, int start, int end) {
    }
}
