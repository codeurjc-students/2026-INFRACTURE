package es.codeurjc.infracture.execution.application;

// What a container consumes now. CPU is a percentage of one core, as Docker reports it. Null means no reading.
public record ResourceUsage(Double cpuPercent, Long memoryBytes) {
}
