package com.shopsmart.assistant.chat;

import com.shopsmart.assistant.shopping.RecommendationService;
import com.shopsmart.kit.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final RecommendationService recommender;
    private final ChatService chat;

    public AssistantController(RecommendationService recommender, ChatService chat) {
        this.recommender = recommender;
        this.chat = chat;
    }

    public record RecommendRequest(@NotBlank @Size(max = 1000) String query) {}

    public record ChatRequest(String sessionId, @NotBlank @Size(max = 2000) String message) {}

    /** Public. Returns the brief's JSON: interpretedRequirement, budget, recommendations[], clarifyingQuestion. */
    @PostMapping("/recommend")
    public RecommendationService.RecommendResponse recommend(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RecommendRequest r) {
        return recommender.recommend(r.query(), user == null ? null : user.id(), null);
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ChatRequest r) {
        return chat.chat(user, r.sessionId(), r.message());
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions(@AuthenticationPrincipal CurrentUser user) { return chat.sessions(user.id()); }

    @GetMapping("/sessions/{id}/messages")
    public List<Map<String, Object>> messages(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return chat.messages(user.id(), id);
    }
}
