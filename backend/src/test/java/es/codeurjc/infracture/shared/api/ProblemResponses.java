package es.codeurjc.infracture.shared.api;

import static org.hamcrest.Matchers.equalTo;

import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.specification.ResponseSpecification;
import org.hamcrest.Matcher;

public final class ProblemResponses {

    private ProblemResponses() {
    }

    public static ResponseSpecification problem(int status, Matcher<?> detail) {
        return new ResponseSpecBuilder()
            .expectStatusCode(status)
            .expectContentType("application/problem+json")
            .expectBody("status", equalTo(status))
            .expectBody("detail", detail)
            .build();
    }

    public static ResponseSpecification problem(int status, String detail) {
        return problem(status, equalTo(detail));
    }
}
