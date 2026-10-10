package com.shopsmart.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Loads the sample policy documents (classpath:kb) the first time, so the RAG demo works out of the box. */
@Component
@ConditionalOnProperty(name = "shopsmart.seed.demo-data", havingValue = "true")
public class SampleKnowledgeLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SampleKnowledgeLoader.class);

    private record Sample(String file, String title, String category, String region, String version, String effective, String expiry) {}

    private static final List<Sample> SAMPLES = List.of(
            new Sample("return-policy-v1.md", "Return Policy", "RETURN", "GLOBAL", "1.0", "2024-01-01", "2025-12-31"),
            new Sample("return-policy-v2.md", "Return Policy", "RETURN", "GLOBAL", "2.2", "2026-01-01", null),
            new Sample("return-policy-india.md", "Return Policy India Addendum", "RETURN", "IN", "1.3", "2026-01-01", null),
            new Sample("shipping-policy.md", "Shipping Policy", "SHIPPING", "GLOBAL", "3.1", "2026-01-01", null),
            new Sample("refund-policy.md", "Refund Policy", "REFUND", "GLOBAL", "2.0", "2026-01-01", null),
            new Sample("warranty-electronics.md", "Electronics Warranty Terms", "WARRANTY", "GLOBAL", "1.4", "2026-01-01", null),
            new Sample("membership-benefits.json", "Membership Benefits", "MEMBERSHIP", "GLOBAL", "1.3", "2026-01-01", null),
            new Sample("delivery-sla.md", "Delivery SLA", "DELIVERY_SLA", "GLOBAL", "1.2", "2026-01-01", null),
            new Sample("seller-guidelines.md", "Seller Guidelines", "SELLER_GUIDELINE", "GLOBAL", "1.0", "2026-01-01", null),
            new Sample("laptop-care-manual.md", "Laptop Care and Setup Guide", "PRODUCT_MANUAL", "GLOBAL", "1.0", "2026-01-01", null));

    private final JdbcClient jdbc;
    private final DocumentService documents;
    private final IngestionService ingestion;

    public SampleKnowledgeLoader(JdbcClient jdbc, DocumentService documents, IngestionService ingestion) {
        this.jdbc = jdbc;
        this.documents = documents;
        this.ingestion = ingestion;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int loaded = 0;
        for (Sample s : SAMPLES) {
            boolean exists = jdbc.sql("""
                    SELECT EXISTS (SELECT 1 FROM kb_documents WHERE title = :title AND version = :version AND region = :region)
                    """).param("title", s.title()).param("version", s.version()).param("region", s.region())
                    .query(Boolean.class).single();
            if (exists) continue;
            byte[] data = new ClassPathResource("kb/" + s.file()).getInputStream().readAllBytes();
            long id = documents.create(data, s.file(), new DocumentService.Meta(s.title(), s.category(), s.region(), s.version(),
                    LocalDate.parse(s.effective()), s.expiry() == null ? null : LocalDate.parse(s.expiry()), null), null);
            ingestion.ingestNow(id);
            loaded++;
        }
        if (loaded > 0) log.info("Loaded {} sample policy documents", loaded);
    }
}
