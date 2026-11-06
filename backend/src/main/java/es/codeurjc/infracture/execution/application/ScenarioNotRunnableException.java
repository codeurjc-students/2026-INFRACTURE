package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.project.domain.ValidationIssue;
import java.util.List;

// Run found errors that block it. The issues travel with the exception so the client can mark them.
public class ScenarioNotRunnableException extends RuntimeException {

    // Exceptions are never serialized here, and ValidationIssue is not Serializable.
    private final transient List<ValidationIssue> issues;

    public ScenarioNotRunnableException(List<ValidationIssue> issues) {
        super("The scenario has errors that block Run");
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }
}
