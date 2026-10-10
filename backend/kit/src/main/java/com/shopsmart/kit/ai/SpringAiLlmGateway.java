package com.shopsmart.kit.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringAiLlmGateway implements LlmGateway {
    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmGateway.class);

    private final ChatClient client;
    private final String model;
    private final boolean enabled;

    public SpringAiLlmGateway(ObjectProvider<ChatClient.Builder> builder,
                              @Value("${spring.ai.openai.api-key:}") String apiKey,
                              @Value("${spring.ai.openai.chat.options.model:unknown}") String model,
                              @Value("${shopsmart.ai.enabled:true}") boolean flag) {
        ChatClient.Builder b = builder.getIfAvailable();
        boolean hasKey = apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-not-set");
        this.enabled = flag && hasKey && b != null;
        this.client = enabled ? b.build() : null;
        this.model = model;
        log.info("LLM gateway: {} (model {})", enabled ? "ON" : "OFF - using deterministic fallbacks", model);
    }

    @Override
    public boolean available() { return enabled; }

    @Override
    public String modelName() { return enabled ? model : "fallback-rules"; }

    @Override
    public Optional<String> complete(String system, String user) {
        if (!enabled) return Optional.empty();
        try {
            String out = client.prompt().system(clean(system)).user(clean(user)).call().content();
            return Optional.ofNullable(out).filter(s -> !s.isBlank());
        } catch (Exception e) {
            log.warn("LLM call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> completeJson(String system, String user, Class<T> type) {
        if (!enabled) return Optional.empty();
        try {
            T out = client.prompt().system(clean(system)).user(clean(user)).call().entity(type);
            return Optional.ofNullable(out);
        } catch (Exception e) {
            log.warn("LLM structured call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Curly braces are template markers in Spring AI prompts, so user text must not contain them. */
    static String clean(String text) {
        return text == null ? "" : text.replace('{', '(').replace('}', ')');
    }
}
