package com.shopsmart.kit.policy;

import com.shopsmart.kit.security.CurrentUser;

/**
 * The single contract between the assistant slice (Aman) and the RAG slice (Nimisha).
 * The knowledge module provides the real @Primary bean; the stub below is used until then.
 */
public interface PolicyAnswerService {
    PolicyAnswer ask(String question, CurrentUser userOrNull);
}
