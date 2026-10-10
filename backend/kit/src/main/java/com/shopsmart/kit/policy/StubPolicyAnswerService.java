package com.shopsmart.kit.policy;

import com.shopsmart.kit.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.List;

/** Canned answer so the assistant slice can be built and tested before the RAG slice lands. */
@Component
public class StubPolicyAnswerService implements PolicyAnswerService {
    @Override
    public PolicyAnswer ask(String question, CurrentUser userOrNull) {
        return new PolicyAnswer("Policy answers are not connected yet (stub).", "LOW", List.of(), true);
    }
}
