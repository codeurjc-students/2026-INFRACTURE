package es.codeurjc.infracture.project.api;

import es.codeurjc.infracture.project.application.ProjectNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProjectController.class)
class ProjectExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    ProblemDetail handleMissingProject(ProjectNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleInvalidProject(ConstraintViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
