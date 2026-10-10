package com.shopsmart.knowledge;

import com.shopsmart.kit.ai.Embedder;
import com.shopsmart.kit.web.Rows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** Parse, chunk, embed and store a document. The upload API returns at once; this runs in the background. */
@Service
public class IngestionService {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final JdbcClient jdbc;
    private final DocumentParser parser;
    private final Chunker chunker;
    private final Embedder embedder;
    private final TransactionTemplate tx;

    public IngestionService(JdbcClient jdbc, DocumentParser parser, Chunker chunker, Embedder embedder, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.chunker = chunker;
        this.embedder = embedder;
        this.tx = tx;
    }

    @Async
    public void ingestAsync(long documentId) { ingestNow(documentId); }

    /** Synchronous version, also used by the sample knowledge loader. Never throws: failures are stored on the row. */
    public void ingestNow(long documentId) {
        try {
            Map<String, Object> doc = jdbc.sql("""
                    SELECT title, category, region, version, effective_date AS eff, source_type AS type, file_path AS path
                    FROM kb_documents WHERE id = :id
                    """).param("id", documentId).query().singleRow();
            byte[] data = Files.readAllBytes(Paths.get((String) doc.get("path")));
            List<DocumentParser.Section> sections = parser.parse((String) doc.get("type"), data);
            List<Chunker.Chunk> chunks = chunker.chunk(sections);
            if (chunks.isEmpty()) throw new IllegalStateException("No readable text found in the file");

            // embed first (slow), then write everything in one short transaction
            float[][] vectors = new float[chunks.size()][];
            for (int i = 0; i < chunks.size(); i++) {
                Chunker.Chunk c = chunks.get(i);
                vectors[i] = embedder.embed(c.section() + ". " + c.content());
            }
            Map<String, Object> meta = Map.of("documentTitle", doc.get("title"), "version", doc.get("version"),
                    "region", doc.get("region"), "category", doc.get("category"));
            String metaJson = Rows.toJson(meta);

            tx.executeWithoutResult(status -> {
                jdbc.sql("DELETE FROM kb_chunks WHERE document_id = :id").param("id", documentId).update();
                for (int i = 0; i < chunks.size(); i++) {
                    Chunker.Chunk c = chunks.get(i);
                    jdbc.sql("""
                            INSERT INTO kb_chunks (document_id, chunk_index, section, content, token_count, metadata, embedding)
                            VALUES (:d, :i, :s, :c, :t, CAST(:m AS jsonb), CAST(:e AS vector))
                            """)
                            .param("d", documentId).param("i", c.index()).param("s", abbreviate(c.section(), 250))
                            .param("c", c.content()).param("t", c.content().split("\\s+").length)
                            .param("m", metaJson).param("e", Embedder.literal(vectors[i])).update();
                }
                jdbc.sql("UPDATE kb_documents SET status = 'APPROVED', chunk_count = :n, error_message = NULL WHERE id = :id")
                        .param("n", chunks.size()).param("id", documentId).update();
                // a newer effective version of the same title and region replaces the older ones
                jdbc.sql("""
                        UPDATE kb_documents SET status = 'SUPERSEDED'
                        WHERE title = :t AND region = :r AND id <> :id AND status = 'APPROVED'
                          AND effective_date <= (SELECT effective_date FROM kb_documents WHERE id = :id)
                        """).param("t", doc.get("title")).param("r", doc.get("region")).param("id", documentId).update();
            });
            log.info("Ingested document {} into {} chunks", documentId, chunks.size());
        } catch (Exception e) {
            log.warn("Ingestion of document {} failed: {}", documentId, e.getMessage());
            jdbc.sql("UPDATE kb_documents SET status = 'FAILED', error_message = :m WHERE id = :id")
                    .param("m", abbreviate(String.valueOf(e.getMessage()), 500)).param("id", documentId).update();
        }
    }

    private static String abbreviate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }
}
