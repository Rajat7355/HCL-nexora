package com.shopsmart.kit.ai;

import java.util.Optional;

/**
 * Provider-agnostic access to the chat model (Spring AI ChatClient underneath).
 * Every method returns empty when no model is configured or the call fails, so each slice
 * keeps a deterministic fallback and the demo never breaks.
 */
public interface LlmGateway {
    boolean available();

    String modelName();

    Optional<String> complete(String system, String user);

    /** Asks the model for JSON and maps it to the given record. Callers must still validate the result. */
    <T> Optional<T> completeJson(String system, String user, Class<T> type);
}
