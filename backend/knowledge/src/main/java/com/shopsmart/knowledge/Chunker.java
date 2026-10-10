package com.shopsmart.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Heading-aware chunking: one chunk per short section; long sections are split on sentence edges with overlap. */
@Component
public class Chunker {
    public static final int MAX_CHARS = 900;
    public static final int OVERLAP_CHARS = 150;

    public record Chunk(int index, String section, String content) {}

    public List<Chunk> chunk(List<DocumentParser.Section> sections) {
        List<Chunk> out = new ArrayList<>();
        int idx = 0;
        for (DocumentParser.Section s : sections) {
            for (String piece : split(s.text())) {
                out.add(new Chunk(idx++, s.heading(), piece));
            }
        }
        return out;
    }

    static List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        String clean = text.trim();
        if (clean.length() <= MAX_CHARS) {
            if (!clean.isEmpty()) pieces.add(clean);
            return pieces;
        }
        String[] units = clean.split("(?<=[.!?])\\s+|\\n{2,}");
        StringBuilder cur = new StringBuilder();
        for (String unit : units) {
            String u = unit.trim();
            if (u.isEmpty()) continue;
            if (cur.length() > 0 && cur.length() + u.length() + 1 > MAX_CHARS) {
                pieces.add(cur.toString().trim());
                String tail = tail(cur.toString());
                cur.setLength(0);
                cur.append(tail);
            }
            // a single very long unit is cut hard so no chunk grows without limit
            while (u.length() > MAX_CHARS) {
                if (cur.length() > 0) { pieces.add(cur.toString().trim()); cur.setLength(0); }
                pieces.add(u.substring(0, MAX_CHARS));
                u = u.substring(MAX_CHARS - OVERLAP_CHARS);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(u);
        }
        if (cur.toString().trim().length() > 0) pieces.add(cur.toString().trim());
        return pieces;
    }

    private static String tail(String s) {
        if (s.length() <= OVERLAP_CHARS) return s;
        String t = s.substring(s.length() - OVERLAP_CHARS);
        int space = t.indexOf(' ');
        return space > 0 ? t.substring(space + 1) : t;
    }
}
