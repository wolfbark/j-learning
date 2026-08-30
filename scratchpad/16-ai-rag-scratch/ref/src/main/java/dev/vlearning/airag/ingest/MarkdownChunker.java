package dev.vlearning.airag.ingest;

import java.util.ArrayList;
import java.util.List;

import dev.vlearning.airag.RagProperties;
import org.springframework.stereotype.Component;

@Component
public class MarkdownChunker {

    private final RagProperties properties;

    public MarkdownChunker(RagProperties properties) {
        this.properties = properties;
    }

    private record Section(String headingPath, int start, int end) {
    }

    public List<Chunk> chunk(SourceDocument source) {
        String text = source.markdown();
        var chunks = new ArrayList<Chunk>();
        int index = 0;
        for (Section section : sections(text)) {
            for (int[] range : split(text, section.start(), section.end())) {
                String slice = text.substring(range[0], range[1]).strip();
                if (slice.isBlank()) {
                    continue;
                }
                chunks.add(new Chunk(source.name(), section.headingPath(), index++, slice));
            }
        }
        return chunks;
    }

    /** Walk the document once, maintaining the heading stack; each heading opens a new section. */
    private List<Section> sections(String text) {
        var sections = new ArrayList<Section>();
        var stack = new ArrayList<String>();
        int sectionStart = 0;
        String currentPath = "";
        int offset = 0;
        boolean inFence = false;
        for (String line : text.split("\n", -1)) {
            int lineStart = offset;
            offset += line.length() + 1;
            if (line.strip().startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }
            int level = headingLevel(line);
            if (level == 0) {
                continue;
            }
            if (lineStart > sectionStart) {
                sections.add(new Section(currentPath, sectionStart, lineStart));
            }
            while (stack.size() >= level) {
                stack.removeLast();
            }
            while (stack.size() < level - 1) {
                stack.add("");
            }
            stack.add(line.substring(level).strip());
            currentPath = String.join(" > ", stack.stream().filter(s -> !s.isEmpty()).toList());
            sectionStart = lineStart;
        }
        if (sectionStart < text.length()) {
            sections.add(new Section(currentPath, sectionStart, text.length()));
        }
        return sections;
    }

    private static int headingLevel(String line) {
        int hashes = 0;
        while (hashes < line.length() && line.charAt(hashes) == '#') {
            hashes++;
        }
        return (hashes >= 1 && hashes <= 6 && hashes < line.length() && line.charAt(hashes) == ' ') ? hashes : 0;
    }

    /** Break a section into at-most-maxChunkChars ranges at line boundaries, with token overlap. */
    private List<int[]> split(String text, int start, int end) {
        int max = this.properties.maxChunkChars();
        var ranges = new ArrayList<int[]>();
        if (end - start <= max) {
            ranges.add(new int[] { start, end });
            return ranges;
        }
        int from = start;
        while (from < end) {
            int to = Math.min(from + max, end);
            if (to < end) {
                to = boundaryBefore(text, from, to);
            }
            ranges.add(new int[] { from, to });
            if (to >= end) {
                break;
            }
            from = overlapStart(text, from, to);
        }
        return ranges;
    }

    /** Prefer a paragraph break, then a line break, then a sentence end; never split a word. */
    private static int boundaryBefore(String text, int from, int to) {
        int paragraph = text.lastIndexOf("\n\n", to);
        if (paragraph > from + (to - from) / 3) {
            return paragraph;
        }
        int line = text.lastIndexOf('\n', to);
        if (line > from + (to - from) / 3) {
            return line;
        }
        int sentence = text.lastIndexOf(". ", to);
        if (sentence > from + (to - from) / 3) {
            return sentence + 1;
        }
        int space = text.lastIndexOf(' ', to);
        return space > from ? space : to;
    }

    /**
     * Rewind overlapTokens tokens from the end of the previous chunk — but never past its midpoint,
     * or a chunk that ended early at a paragraph break barely advances and the splitter shreds the
     * document into thousands of near-duplicates.
     */
    private int overlapStart(String text, int from, int to) {
        int tokens = this.properties.overlapTokens();
        int floor = from + (to - from) / 2;
        int at = to;
        int seen = 0;
        while (at > floor && seen < tokens) {
            at--;
            if (Character.isWhitespace(text.charAt(at)) && at + 1 < text.length()
                    && !Character.isWhitespace(text.charAt(at + 1))) {
                seen++;
            }
        }
        // The clamp above can land inside a word; walk forward to the next word start so no chunk
        // ever begins with half a token.
        if (at > 0 && !Character.isWhitespace(text.charAt(at - 1))) {
            while (at < to && !Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }
        while (at < to && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
        return at;
    }
}
