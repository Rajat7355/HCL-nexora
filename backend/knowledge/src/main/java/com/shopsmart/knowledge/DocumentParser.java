package com.shopsmart.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Turns an uploaded PDF, Markdown or JSON file into titled sections. */
@Component
public class DocumentParser {
    public record Section(String heading, String text) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PDF_HEADING = Pattern.compile("^(\\d+(\\.\\d+)*[.)]?\\s+\\p{Lu}.{2,80}|\\p{Lu}[\\p{Lu}\\s&/-]{5,80})$");

    public List<Section> parse(String sourceType, byte[] data) throws IOException {
        return switch (sourceType) {
            case "PDF" -> parsePdf(data);
            case "MARKDOWN" -> parseMarkdown(new String(data, StandardCharsets.UTF_8));
            case "JSON" -> parseJson(new String(data, StandardCharsets.UTF_8));
            default -> throw new IOException("Unsupported source type " + sourceType);
        };
    }

    // ---------------------------------------------------------------- PDF
    List<Section> parsePdf(byte[] data) throws IOException {
        String text;
        try (PDDocument doc = Loader.loadPDF(data)) {
            text = new PDFTextStripper().getText(doc);
        }
        List<Section> out = new ArrayList<>();
        String heading = "Document";
        StringBuilder body = new StringBuilder();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) { body.append('\n'); continue; }
            if (line.length() <= 90 && PDF_HEADING.matcher(line).matches()) {
                flush(out, heading, body);
                heading = line;
            } else {
                body.append(line).append(' ');
            }
        }
        flush(out, heading, body);
        return out;
    }

    // ----------------------------------------------------------- Markdown
    List<Section> parseMarkdown(String md) {
        List<Section> out = new ArrayList<>();
        String h1 = "";
        String heading = "Overview";
        StringBuilder body = new StringBuilder();
        for (String raw : md.split("\\r?\\n")) {
            String line = raw.stripTrailing();
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') level++;
                String title = line.substring(level).trim();
                flush(out, heading, body);
                if (level == 1) { h1 = title; heading = title; }
                else heading = h1.isEmpty() ? title : h1 + " > " + title;
            } else {
                body.append(line).append('\n');
            }
        }
        flush(out, heading, body);
        return out;
    }

    // --------------------------------------------------------------- JSON
    List<Section> parseJson(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        List<Section> out = new ArrayList<>();
        if (root.isArray()) {
            int n = 1;
            for (JsonNode item : root) {
                String heading = firstText(item, "title", "name", "section", "id");
                out.add(new Section(heading != null ? heading : "Item " + n, flatten(item, "")));
                n++;
            }
        } else if (root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            StringBuilder scalars = new StringBuilder();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue().isContainerNode()) {
                    out.add(new Section(e.getKey(), flatten(e.getValue(), "")));
                } else {
                    scalars.append(e.getKey()).append(": ").append(e.getValue().asText()).append('\n');
                }
            }
            if (scalars.length() > 0) out.add(0, new Section("Overview", scalars.toString()));
        } else {
            out.add(new Section("Document", root.asText()));
        }
        out.removeIf(s -> s.text().isBlank());
        if (out.isEmpty()) throw new IOException("The JSON file has no readable content");
        return out;
    }

    private static String firstText(JsonNode node, String... keys) {
        for (String k : keys) {
            if (node.has(k) && node.get(k).isValueNode()) return node.get(k).asText();
        }
        return null;
    }

    static String flatten(JsonNode node, String prefix) {
        StringBuilder sb = new StringBuilder();
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String key = prefix.isEmpty() ? e.getKey() : prefix + " " + e.getKey();
                if (e.getValue().isContainerNode()) sb.append(flatten(e.getValue(), key));
                else sb.append(key).append(": ").append(e.getValue().asText()).append(".\n");
            }
        } else if (node.isArray()) {
            int i = 1;
            for (JsonNode v : node) {
                if (v.isContainerNode()) sb.append(flatten(v, prefix));
                else sb.append(prefix.isEmpty() ? "item " + i : prefix).append(": ").append(v.asText()).append(".\n");
                i++;
            }
        } else {
            sb.append(prefix).append(": ").append(node.asText()).append(".\n");
        }
        return sb.toString();
    }

    private static void flush(List<Section> out, String heading, StringBuilder body) {
        String text = body.toString().replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (!text.isEmpty()) out.add(new Section(heading, text));
        body.setLength(0);
    }
}
