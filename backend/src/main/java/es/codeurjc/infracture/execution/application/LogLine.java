package es.codeurjc.infracture.execution.application;

import java.time.Instant;

// One line a service of the plan printed, with the time Docker recorded it.
public record LogLine(String service, Instant at, String text) {
}
