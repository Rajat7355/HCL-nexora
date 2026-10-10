package com.shopsmart.kit.policy;

import java.util.List;

/** Contract answer from the brief: answer, confidence, sources[document, section, version], followUpRequired. */
public record PolicyAnswer(String answer, String confidence, List<Source> sources, boolean followUpRequired) {
    public record Source(String document, String section, String version) {}
}
