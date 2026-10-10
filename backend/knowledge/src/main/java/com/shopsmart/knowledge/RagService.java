package com.shopsmart.knowledge;

import com.shopsmart.kit.ai.AuditLogger;
import com.shopsmart.kit.ai.Embedder;
import com.shopsmart.kit.ai.LlmGateway;
import com.shopsmart.kit.policy.PolicyAnswer;
import com.shopsmart.kit.policy.PolicyAnswerService;
import com.shopsmart.kit.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Grounded policy answers: vector search over approved, in-date chunks, hybrid re-scoring, then an answer that
 * only uses the retrieved text. Low evidence means a refusal, not a guess.
 */
@Primary
@Service
public class RagService implements PolicyAnswerService {
    static final String REFUSAL = "I couldn't find this in the current ShopSmart policy documents, so I can't answer it reliably. "
            + "Please rephrase with more detail (for example the product type or your region) or contact support.";

    private static final Set<String> STOP = Set.of("the", "and", "for", "are", "can", "what", "how", "does", "did", "will", "with",
            "that", "this", "have", "has", "you", "your", "from", "about", "when", "which", "who", "why", "was", "were", "any",
            "get", "its", "not", "but", "out", "into", "there", "than", "then", "them", "they", "our", "also", "may", "should", "would");

    private final JdbcClient jdbc;
    private final Embedder embedder;
    private final LlmGateway llm;
    private final AuditLogger audit;
    private final double high;
    private final double medium;
    private final double min;

    public RagService(JdbcClient jdbc, Embedder embedder, LlmGateway llm, AuditLogger audit,
                      @Value("${shopsmart.rag.high:0.55}") double high,
                      @Value("${shopsmart.rag.medium:0.42}") double medium,
                      @Value("${shopsmart.rag.min:0.30}") double min) {
        this.jdbc = jdbc;
        this.embedder = embedder;
        this.llm = llm;
        this.audit = audit;
        this.high = high;
        this.medium = medium;
        this.min = min;
    }

    /** One retrieved chunk with its scores. */
    record Hit(String document, String section, String version, String content, double vector, double lexical, double score) {}

    @Override
    public PolicyAnswer ask(String question, CurrentUser user) {
        return ask(question, user == null ? null : user.region(), user == null ? null : user.id());
    }

    public PolicyAnswer ask(String question, String region, Long userId) {
        long start = System.currentTimeMillis();
        String q = question == null ? "" : question.trim();
        String reg = region == null || region.isBlank() ? "GLOBAL" : region.trim().toUpperCase(Locale.ROOT);
        if (q.length() < 3) {
            PolicyAnswer a = new PolicyAnswer("Please ask a full question about returns, shipping, refunds, warranty or membership.", "LOW", List.of(), true);
            audit.log(userId, null, "RAG_ANSWER", q, Map.of("region", reg), List.of(), a, "LOW", "REFUSED", null, llm.modelName(), System.currentTimeMillis() - start);
            return a;
        }

        List<Hit> hits = retrieve(q, reg);
        List<Map<String, Object>> context = new ArrayList<>();
        for (Hit h : hits) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("document", h.document());
            c.put("section", h.section());
            c.put("version", h.version());
            c.put("vectorScore", round(h.vector()));
            c.put("keywordScore", round(h.lexical()));
            c.put("score", round(h.score()));
            c.put("text", h.content());
            context.add(c);
        }

        double best = hits.isEmpty() ? 0 : hits.get(0).score();
        PolicyAnswer answer;
        String status = "OK";
        if (best < min) {
            answer = new PolicyAnswer(REFUSAL, "LOW", List.of(), true);
            status = "REFUSED";
        } else {
            List<Hit> used = new ArrayList<>(hits.stream().filter(h -> h.score() >= Math.max(min, best - 0.15)).limit(3).toList());
            String text = null;
            if (llm.available()) text = generate(q, used);
            if (text != null && text.trim().equals("NOT_FOUND")) {
                answer = new PolicyAnswer(REFUSAL, "LOW", List.of(), true);
                status = "REFUSED";
            } else {
                if (text == null || text.isBlank()) text = extractive(q, used.get(0));
                String confidence = best >= high ? "HIGH" : best >= medium ? "MEDIUM" : "LOW";
                answer = new PolicyAnswer(text.trim(), confidence, sources(used), !confidence.equals("HIGH"));
            }
        }
        audit.log(userId, null, "RAG_ANSWER", q, Map.of("question", q, "region", reg), context, answer,
                answer.confidence(), status, null, llm.modelName(), System.currentTimeMillis() - start);
        return answer;
    }

    List<Hit> retrieve(String question, String region) {
        String literal = Embedder.literal(embedder.embed(question));
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT d.title, c.section, d.version, c.content,
                       1 - (c.embedding <=> CAST(:v AS vector)) AS score
                FROM kb_chunks c JOIN kb_documents d ON d.id = c.document_id
                WHERE d.status = 'APPROVED'
                  AND d.effective_date <= current_date
                  AND (d.expiry_date IS NULL OR d.expiry_date >= current_date)
                  AND d.region IN ('GLOBAL', :region)
                ORDER BY c.embedding <=> CAST(:v AS vector)
                LIMIT 8
                """).param("v", literal).param("region", region).query().listOfRows();
        Set<String> terms = terms(question);
        List<Hit> hits = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            String content = (String) r.get("content");
            String section = (String) r.get("section");
            double vec = ((Number) r.get("score")).doubleValue();
            double lex = coverage(terms, (section + " " + content).toLowerCase(Locale.ROOT));
            hits.add(new Hit((String) r.get("title"), section, (String) r.get("version"), content, vec, lex, 0.65 * vec + 0.35 * lex));
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits;
    }

    private String generate(String question, List<Hit> used) {
        StringBuilder src = new StringBuilder();
        int n = 1;
        for (Hit h : used) {
            src.append('[').append(n++).append("] ").append(h.document()).append(" (").append(h.section())
               .append(", v").append(h.version()).append("): ").append(h.content()).append("\n\n");
        }
        String system = "You are ShopSmart's policy assistant. Answer ONLY from the numbered sources. "
                + "If the sources do not contain the answer, reply with exactly NOT_FOUND. "
                + "Be concise (at most 4 sentences), state time limits and conditions, and never invent facts.";
        String user = "Question: " + question + "\n\nSources:\n" + src;
        return llm.complete(system, user).orElse(null);
    }

    /** No-LLM fallback: the sentences of the best chunk that overlap most with the question. */
    static String extractive(String question, Hit best) {
        String content = best.content().replaceAll("\\s+", " ").trim();
        if (content.length() <= 420) return content;
        Set<String> terms = terms(question);
        String[] sentences = content.split("(?<=[.!?])\\s+");
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < sentences.length; i++) order.add(i);
        order.sort((a, b) -> Double.compare(coverage(terms, sentences[b].toLowerCase(Locale.ROOT)), coverage(terms, sentences[a].toLowerCase(Locale.ROOT))));
        List<Integer> pick = new ArrayList<>(order.subList(0, Math.min(3, order.size())));
        pick.sort(Integer::compare);
        StringBuilder sb = new StringBuilder();
        for (int i : pick) sb.append(sentences[i]).append(' ');
        return sb.toString().trim();
    }

    static List<PolicyAnswer.Source> sources(List<Hit> used) {
        Set<String> seen = new LinkedHashSet<>();
        List<PolicyAnswer.Source> out = new ArrayList<>();
        for (Hit h : used) {
            String key = h.document() + "|" + h.section() + "|" + h.version();
            if (seen.add(key)) out.add(new PolicyAnswer.Source(h.document(), h.section(), h.version()));
        }
        return out;
    }

    static Set<String> terms(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (t.length() > 2 && !STOP.contains(t)) out.add(stem(t));
        }
        return out;
    }

    private static String stem(String t) {
        if (t.length() > 5 && t.endsWith("ing")) return t.substring(0, t.length() - 3);
        if (t.length() > 4 && t.endsWith("ed")) return t.substring(0, t.length() - 2);
        if (t.length() > 3 && t.endsWith("s")) return t.substring(0, t.length() - 1);
        return t;
    }

    /** Share of question terms found (as word starts) in the text. */
    static double coverage(Set<String> terms, String lowerText) {
        if (terms.isEmpty()) return 0;
        int hit = 0;
        for (String t : terms) if (lowerText.contains(t)) hit++;
        return (double) hit / terms.size();
    }

    private static double round(double d) { return Math.round(d * 1000.0) / 1000.0; }
}
