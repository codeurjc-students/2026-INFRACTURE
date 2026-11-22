package es.codeurjc.infracture.execution.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.execution.domain.ExecutionEvent;
import es.codeurjc.infracture.execution.domain.ExecutionEvent.Kind;
import es.codeurjc.infracture.execution.persistence.ExecutionEventRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

// The event table in memory, for tests that run the journal without a database. Ids grow from 1 as in
// PostgreSQL, and each query answers as its repository method does.
final class InMemoryExecutionEvents {

    final List<ExecutionEvent> saved = new ArrayList<>();
    final ExecutionEventRepository repository = mock(ExecutionEventRepository.class);

    InMemoryExecutionEvents() {
        when(repository.save(any(ExecutionEvent.class))).thenAnswer(invocation -> {
            ExecutionEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", (long) saved.size() + 1);
            saved.add(event);
            return event;
        });
        when(repository.findByExecutionIdAndKindNotOrderById(anyLong(), any()))
                .thenAnswer(invocation -> of(invocation.getArgument(0)).stream()
                        .filter(event -> event.getKind() != invocation.getArgument(1))
                        .toList());
        when(repository.findTop200ByExecutionIdAndIdGreaterThanOrderById(anyLong(), anyLong())).thenAnswer(
                invocation -> of(invocation.getArgument(0)).stream()
                        .filter(event -> event.getId() > (long) invocation.getArgument(1)).limit(200).toList());
        when(repository.existsByIdAndExecutionId(anyLong(), anyLong()))
                .thenAnswer(invocation -> of(invocation.getArgument(1)).stream()
                        .anyMatch(event -> event.getId().equals(invocation.getArgument(0))));
        when(repository.findFirstByExecutionIdOrderByIdDesc(anyLong()))
                .thenAnswer(invocation -> of(invocation.getArgument(0)).stream().reduce((first, second) -> second));
        when(repository.findFirstByExecutionIdAndKindOrderByAtDescIdDesc(anyLong(), any()))
                .thenAnswer(invocation -> of(invocation.getArgument(0)).stream()
                        .filter(event -> event.getKind() == invocation.getArgument(1))
                        .max(Comparator.comparing(ExecutionEvent::getAt).thenComparing(ExecutionEvent::getId)));
        when(repository.countByExecutionIdAndComponentKeyAndKind(anyLong(), any(), any())).thenAnswer(
                invocation -> count(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        when(repository.countByExecutionIdAndComponentKeyIsNullAndKind(anyLong(), any()))
                .thenAnswer(invocation -> count(invocation.getArgument(0), null, invocation.getArgument(1)));
    }

    List<ExecutionEvent> of(Long executionId) {
        return saved.stream().filter(event -> event.getExecutionId().equals(executionId)).toList();
    }

    List<ExecutionEvent> of(Long executionId, Kind kind) {
        return of(executionId).stream().filter(event -> event.getKind() == kind).toList();
    }

    // The EVENT types of one execution, in the order they were written.
    List<String> types(Long executionId) {
        return of(executionId, Kind.EVENT).stream().map(ExecutionEvent::getType).toList();
    }

    Optional<ExecutionEvent> last() {
        return saved.isEmpty() ? Optional.empty() : Optional.of(saved.getLast());
    }

    private long count(Long executionId, UUID componentKey, Kind kind) {
        return of(executionId, kind).stream().filter(event -> Objects.equals(event.getComponentKey(), componentKey))
                .count();
    }
}
