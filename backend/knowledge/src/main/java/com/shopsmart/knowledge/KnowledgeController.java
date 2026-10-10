package com.shopsmart.knowledge;

import com.shopsmart.kit.policy.PolicyAnswer;
import com.shopsmart.kit.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class KnowledgeController {
    private final DocumentService documents;
    private final IngestionService ingestion;
    private final RagService rag;

    public KnowledgeController(DocumentService documents, IngestionService ingestion, RagService rag) {
        this.documents = documents;
        this.ingestion = ingestion;
        this.rag = rag;
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question, @Size(max = 16) String region) {}

    public record PatchRequest(String status, LocalDate expiryDate) {}

    /** Public: grounded policy Q&A. Region defaults to the logged-in user's region. */
    @PostMapping("/knowledge/ask")
    public PolicyAnswer ask(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AskRequest r) {
        String region = r.region() != null && !r.region().isBlank() ? r.region() : (user == null ? null : user.region());
        return rag.ask(r.question(), region, user == null ? null : user.id());
    }

    @PostMapping(value = "/admin/knowledge/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> upload(@AuthenticationPrincipal CurrentUser user,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam String title,
                                      @RequestParam String category,
                                      @RequestParam(required = false) String region,
                                      @RequestParam String version,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                                      @RequestParam(required = false) String productSku) throws IOException {
        long id = documents.create(file.getBytes(), file.getOriginalFilename(),
                new DocumentService.Meta(title, category, region, version, effectiveDate, expiryDate, productSku), user.id());
        ingestion.ingestAsync(id);
        return documents.get(id);
    }

    @GetMapping("/admin/knowledge/documents")
    public List<Map<String, Object>> list() { return documents.list(); }

    @GetMapping("/admin/knowledge/documents/{id}")
    public Map<String, Object> get(@PathVariable long id) { return documents.get(id); }

    @PatchMapping("/admin/knowledge/documents/{id}")
    public Map<String, Object> patch(@PathVariable long id, @RequestBody PatchRequest r) {
        return documents.patch(id, r.status(), r.expiryDate());
    }

    @DeleteMapping("/admin/knowledge/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { documents.delete(id); }
}
