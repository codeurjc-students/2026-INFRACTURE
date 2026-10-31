package es.codeurjc.infracture.project.domain;

public record ValidationIssue(Severity severity, String code, String message, Target target, String field) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public enum Kind {
        COMPONENT,
        CONNECTION,
        WORKLOAD,
        SCENARIO
    }

    public record Target(Kind kind, String key) {
    }
}
