package com.shopsmart.knowledge;

import com.shopsmart.kit.error.ApiException;
import com.shopsmart.kit.web.Rows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DocumentService {
    static final Set<String> CATEGORIES = Set.of("RETURN", "SHIPPING", "REFUND", "WARRANTY", "MEMBERSHIP",
            "PRODUCT_MANUAL", "SELLER_GUIDELINE", "DELIVERY_SLA");

    public record Meta(String title, String category, String region, String version,
                       LocalDate effectiveDate, LocalDate expiryDate, String productSku) {}

    private final JdbcClient jdbc;
    private final Path storageDir;

    public DocumentService(JdbcClient jdbc, @Value("${shopsmart.storage-dir:./data/uploads}") String dir) {
        this.jdbc = jdbc;
        this.storageDir = Paths.get(dir);
    }

    /** Validates, stores the file, inserts the PROCESSING row and returns its id. Ingestion is started by the caller. */
    public long create(byte[] data, String originalFilename, Meta m, Long uploadedBy) {
        String name = originalFilename == null ? "upload" : Paths.get(originalFilename).getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        String type;
        if (lower.endsWith(".pdf")) type = "PDF";
        else if (lower.endsWith(".md") || lower.endsWith(".markdown")) type = "MARKDOWN";
        else if (lower.endsWith(".json")) type = "JSON";
        else throw ApiException.badRequest("Only PDF, Markdown (.md) and JSON files are supported");
        if (data == null || data.length == 0) throw ApiException.badRequest("The file is empty");
        if (m.title() == null || m.title().isBlank()) throw ApiException.badRequest("Title is required");
        if (m.category() == null || !CATEGORIES.contains(m.category())) {
            throw ApiException.badRequest("Category must be one of " + CATEGORIES);
        }
        if (m.version() == null || m.version().isBlank()) throw ApiException.badRequest("Version is required");
        if (m.effectiveDate() == null) throw ApiException.badRequest("Effective date is required");
        if (m.expiryDate() != null && !m.expiryDate().isAfter(m.effectiveDate())) {
            throw ApiException.badRequest("Expiry date must be after the effective date");
        }
        String region = m.region() == null || m.region().isBlank() ? "GLOBAL" : m.region().trim().toUpperCase(Locale.ROOT);
        String checksum = sha256(data);

        Integer dup = jdbc.sql("SELECT count(*) FROM kb_documents WHERE checksum_sha256 = :c AND status <> 'FAILED'")
                .param("c", checksum).query(Integer.class).single();
        if (dup > 0) throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT", "This exact file was already uploaded");
        Integer sameVersion = jdbc.sql("SELECT count(*) FROM kb_documents WHERE title = :t AND version = :v AND region = :r")
                .param("t", m.title().trim()).param("v", m.version().trim()).param("r", region).query(Integer.class).single();
        if (sameVersion > 0) throw new ApiException(HttpStatus.CONFLICT, "VERSION_EXISTS", "This title already has that version for this region");

        Long productId = null;
        if (m.productSku() != null && !m.productSku().isBlank()) {
            productId = jdbc.sql("SELECT id FROM products WHERE sku = :s").param("s", m.productSku().trim())
                    .query(Long.class).optional().orElseThrow(() -> ApiException.notFound("Product " + m.productSku()));
        }

        String path;
        try {
            Files.createDirectories(storageDir);
            Path target = storageDir.resolve(checksum.substring(0, 12) + "_" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
            Files.write(target, data);
            path = target.toString();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Could not store the uploaded file");
        }

        return jdbc.sql("""
                INSERT INTO kb_documents (title, category, region, version, effective_date, expiry_date, source_type,
                                          original_filename, file_path, checksum_sha256, product_id, status, uploaded_by)
                VALUES (:t, :c, :r, :v, :eff, :exp, :st, :fn, :fp, :ck, :pid, 'PROCESSING', :ub) RETURNING id
                """)
                .param("t", m.title().trim()).param("c", m.category()).param("r", region).param("v", m.version().trim())
                .param("eff", java.sql.Date.valueOf(m.effectiveDate()))
                .param("exp", m.expiryDate() == null ? null : java.sql.Date.valueOf(m.expiryDate()))
                .param("st", type).param("fn", name).param("fp", path).param("ck", checksum)
                .param("pid", productId).param("ub", uploadedBy)
                .query(Long.class).single();
    }

    public List<Map<String, Object>> list() {
        return jdbc.sql("""
                SELECT d.id, d.title, d.category, d.region, d.version, d.effective_date AS "effectiveDate", d.expiry_date AS "expiryDate",
                       d.source_type AS "sourceType", d.original_filename AS "filename", d.status, d.chunk_count AS "chunkCount",
                       d.error_message AS error, p.sku AS "productId", d.created_at AS "createdAt"
                FROM kb_documents d LEFT JOIN products p ON p.id = d.product_id
                ORDER BY d.title, d.effective_date DESC, d.id DESC
                """).query().listOfRows();
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT d.id, d.title, d.category, d.region, d.version, d.effective_date AS "effectiveDate", d.expiry_date AS "expiryDate",
                       d.source_type AS "sourceType", d.original_filename AS "filename", d.status, d.chunk_count AS "chunkCount",
                       d.error_message AS error, d.created_at AS "createdAt"
                FROM kb_documents d WHERE d.id = :id
                """).param("id", id).query().listOfRows();
        if (rows.isEmpty()) throw ApiException.notFound("Document");
        Map<String, Object> doc = new java.util.LinkedHashMap<>(rows.get(0));
        doc.put("chunks", Rows.json(jdbc.sql("""
                SELECT chunk_index AS "index", section, content, token_count AS "tokens"
                FROM kb_chunks WHERE document_id = :id ORDER BY chunk_index LIMIT 200
                """).param("id", id).query().listOfRows()));
        return doc;
    }

    public Map<String, Object> patch(long id, String status, LocalDate expiryDate) {
        if (status != null && !Set.of("APPROVED", "SUPERSEDED").contains(status)) {
            throw ApiException.badRequest("Status can be set to APPROVED or SUPERSEDED");
        }
        int n = jdbc.sql("""
                UPDATE kb_documents SET status = COALESCE(:s, status), expiry_date = COALESCE(:e, expiry_date)
                WHERE id = :id AND status <> 'PROCESSING'
                """).param("s", status).param("e", expiryDate == null ? null : java.sql.Date.valueOf(expiryDate)).param("id", id).update();
        if (n == 0) throw ApiException.notFound("Document (or still processing)");
        return get(id);
    }

    public void delete(long id) {
        int n = jdbc.sql("DELETE FROM kb_documents WHERE id = :id").param("id", id).update();
        if (n == 0) throw ApiException.notFound("Document");
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String text(byte[] data) { return new String(data, StandardCharsets.UTF_8); }
}
