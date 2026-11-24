package es.codeurjc.infracture.execution.persistence;

import es.codeurjc.infracture.execution.domain.MetricSample;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricSampleRepository extends JpaRepository<MetricSample, Long> {

    // One page of the series of an execution's nodes, in order, after a cursor and inside a time range.
    List<MetricSample> findByExecutedComponentIdInAndIdGreaterThanAndSampledAtBetweenOrderById(
            Collection<Long> executedComponentIds, long after, Instant from, Instant to, Limit limit);
}
