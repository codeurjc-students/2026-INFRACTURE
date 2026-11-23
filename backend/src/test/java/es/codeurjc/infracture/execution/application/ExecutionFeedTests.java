package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import es.codeurjc.infracture.execution.persistence.ExecutionEventRepository;
import es.codeurjc.infracture.execution.persistence.ExecutionRepository;
import es.codeurjc.infracture.identity.application.UserService;
import org.junit.jupiter.api.Test;

class ExecutionFeedTests {

    private final ExecutionEventRepository events = mock(ExecutionEventRepository.class);
    private final ExecutionRepository executions = mock(ExecutionRepository.class);
    private final UserService users = mock(UserService.class);
    private final ExecutionFeed feed = new ExecutionFeed(events, executions, users);

    @Test
    void sendsNothingToASessionThatIsNoLongerCurrent() {
        when(users.isSessionCurrent(7L, 3)).thenReturn(false);

        ExecutionFeed.Batch batch = feed.next(7L, 3, 12L, 0);

        assertThat(batch.allowed()).isFalse();
        assertThat(batch.events()).isEmpty();
        verifyNoInteractions(events, executions);
    }
}
