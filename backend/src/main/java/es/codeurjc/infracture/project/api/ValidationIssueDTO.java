package es.codeurjc.infracture.project.api;

import es.codeurjc.infracture.project.domain.ValidationIssue.Kind;
import es.codeurjc.infracture.project.domain.ValidationIssue.Severity;

public record ValidationIssueDTO(Severity severity, String code, String message, TargetDTO target, String field) {

    public record TargetDTO(Kind kind, String key) {
    }
}
