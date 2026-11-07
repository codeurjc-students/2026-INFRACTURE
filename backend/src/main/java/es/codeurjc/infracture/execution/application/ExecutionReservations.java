package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.project.application.ScenarioReservations;
import org.springframework.stereotype.Component;

@Component
class ExecutionReservations implements ScenarioReservations {

    private final ExecutionRepository repository;

    ExecutionReservations(ExecutionRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isReserved(Long scenarioId) {
        return repository.existsByScenarioIdAndReleasedAtIsNull(scenarioId);
    }
}
