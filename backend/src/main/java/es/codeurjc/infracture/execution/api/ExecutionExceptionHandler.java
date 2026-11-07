package es.codeurjc.infracture.execution.api;

import es.codeurjc.infracture.execution.application.ScenarioNotRunnableException;
import es.codeurjc.infracture.project.api.ValidationIssueDTO;
import es.codeurjc.infracture.project.api.ValidationIssueDTO.TargetDTO;
import es.codeurjc.infracture.project.domain.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Turns a Run blocked by validation errors into a 422 response that lists the issues.
// It maps the issues by hand instead of injecting ScenarioMapper on purpose: @WebMvcTest tests
// (such as UserControllerTests) start only the web layer but load every @RestControllerAdvice,
// and ScenarioMapper does not exist there, so injecting it would stop those tests from starting.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExecutionExceptionHandler {

    // the request is well formed, but the saved scenario has errors that block Run.
    @ExceptionHandler(ScenarioNotRunnableException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    ProblemDetail handleNotRunnable(ScenarioNotRunnableException exception) {
        List<ValidationIssueDTO> issues = new ArrayList<>();
        for (ValidationIssue issue : exception.getIssues()) {
            issues.add(new ValidationIssueDTO(issue.severity(), issue.code(), issue.message(),
                    new TargetDTO(issue.target().kind(), issue.target().key()), issue.field()));
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                exception.getMessage());
        problem.setProperty("issues", issues);
        return problem;
    }
}
