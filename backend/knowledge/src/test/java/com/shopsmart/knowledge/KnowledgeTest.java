package com.shopsmart.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeTest {
    private final DocumentParser parser = new DocumentParser();
    private final Chunker chunker = new Chunker();

    @Test
    void markdownIsSplitByHeadingWithParentPath() {
        String md = "# Return Policy\n\n## Return window\nThirty days.\n\n## Refunds\nSeven days.\n";
        List<DocumentParser.Section> s = parser.parseMarkdown(md);
        assertEquals(2, s.size());
        assertEquals("Return Policy > Return window", s.get(0).heading());
        assertTrue(s.get(1).text().contains("Seven days"));
    }

    @Test
    void jsonObjectAndArrayBecomeSections() throws Exception {
        List<DocumentParser.Section> s = parser.parseJson("{\"tiers\":[{\"name\":\"Prime\",\"benefits\":[\"Free shipping\"]},{\"name\":\"Basic\",\"benefits\":[\"30 day returns\"]}]}");
        assertEquals(1, s.size());
        assertTrue(s.get(0).text().contains("Free shipping"));
        List<DocumentParser.Section> arr = parser.parseJson("[{\"title\":\"A\",\"body\":\"alpha\"},{\"title\":\"B\",\"body\":\"beta\"}]");
        assertEquals("A", arr.get(0).heading());
        assertEquals(2, arr.size());
    }

    @Test
    void longSectionIsSplitAndOverlaps() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) sb.append("This is sentence number ").append(i).append(" about returns. ");
        List<Chunker.Chunk> chunks = chunker.chunk(List.of(new DocumentParser.Section("Long", sb.toString())));
        assertTrue(chunks.size() > 1);
        for (Chunker.Chunk c : chunks) assertTrue(c.content().length() <= Chunker.MAX_CHARS + Chunker.OVERLAP_CHARS + 50);
        assertEquals(0, chunks.get(0).index());
        assertEquals(chunks.size() - 1, chunks.get(chunks.size() - 1).index());
    }

    @Test
    void shortSectionStaysOneChunk() {
        assertEquals(1, chunker.chunk(List.of(new DocumentParser.Section("S", "One short paragraph."))).size());
    }

    @Test
    void termsDropStopwordsAndCoverageCounts() {
        Set<String> t = RagService.terms("What is the return window for laptops?");
        assertTrue(t.contains("return"));
        assertFalse(t.contains("the"));
        assertEquals(1.0, RagService.coverage(t, "the return window for laptop purchases"), 1e-9);
        assertTrue(RagService.coverage(t, "completely unrelated text") < 0.2);
    }

    @Test
    void extractiveAnswerPicksRelevantSentences() {
        String content = "Opened items are inspected. ".repeat(10) + "Laptops can be returned within 15 days of delivery. " + "Gift cards cannot be returned. ".repeat(3);
        RagService.Hit hit = new RagService.Hit("Return Policy", "Return window", "2.1", content, 0.6, 0.6, 0.6);
        String answer = RagService.extractive("how many days to return a laptop", hit);
        assertTrue(answer.contains("15 days"));
        assertTrue(answer.length() < content.length());
    }
}
