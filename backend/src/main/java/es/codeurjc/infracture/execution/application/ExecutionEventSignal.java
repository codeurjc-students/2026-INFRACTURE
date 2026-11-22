package es.codeurjc.infracture.execution.application;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

// Wakes the event streams of an execution when the database says it has new events (#92, ADR 0017).
// The V17 trigger sends a notice with the execution id after every commit that inserts events; this
// component keeps one connection of its own listening to it, outside the pool. A notice only says
// "read again": the events always come from the table after the stream's cursor, so a lost notice delays
// them and never loses them. While the connection is down, isListening() is false and the streams go back
// to reading on their own every second.
@Component
public class ExecutionEventSignal implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEventSignal.class);

    static final String CHANNEL = "execution_events";
    static final String APPLICATION_NAME = "infracture-execution-events";
    private static final int RECEIVE_TIMEOUT_MILLIS = 1000;
    // A silent connection is checked this often, so a database that went away is noticed.
    private static final int CHECK_AFTER_SILENT_RECEIVES = 10;
    private static final Duration RETRY_PAUSE = Duration.ofSeconds(1);

    private final JdbcConnectionDetails database;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    // Guarded by lock. Only executions with an open stream are kept, so the map does not grow with history.
    private final Map<Long, Watch> watches = new HashMap<>();
    // Guarded by lock. Grows when every stream must read again: after the listener (re)connects.
    private long epoch;
    private volatile boolean listening;
    private volatile boolean running;
    private Thread listener;

    public ExecutionEventSignal(JdbcConnectionDetails database) {
        this.database = database;
    }

    // A stream registers its execution before it first reads, so no notice can fall between the two.
    public void watch(Long executionId) {
        lock.lock();
        try {
            watches.computeIfAbsent(executionId, id -> new Watch()).streams++;
        } finally {
            lock.unlock();
        }
    }

    public void unwatch(Long executionId) {
        lock.lock();
        try {
            Watch watch = watches.get(executionId);
            if (watch != null && --watch.streams == 0) {
                watches.remove(executionId);
            }
        } finally {
            lock.unlock();
        }
    }

    // Changes whenever the execution may have new events. A stream takes it before it reads and waits
    // with it afterwards, so a notice that arrives while it reads is not missed.
    public long version(Long executionId) {
        lock.lock();
        try {
            Watch watch = watches.get(executionId);
            return epoch + (watch == null ? 0 : watch.notices);
        } finally {
            lock.unlock();
        }
    }

    // Returns when the version is no longer the one seen, or after at most the given time.
    public void await(Long executionId, long seen, Duration atMost) throws InterruptedException {
        long left = atMost.toNanos();
        lock.lock();
        try {
            while (left > 0 && version(executionId) == seen) {
                left = changed.awaitNanos(left);
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isListening() {
        return listening;
    }

    @Override
    public void start() {
        running = true;
        listener = Thread.ofVirtual().name("execution-events-listener").start(this::listen);
    }

    @Override
    public void stop() {
        running = false;
        if (listener != null) {
            listener.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void listen() {
        boolean warned = false;
        while (running) {
            try (Connection connection = connect()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("LISTEN " + CHANNEL);
                }
                listening = true;
                warned = false;
                // Events committed while nobody listened have no notice: every stream reads once more.
                wakeEveryStream();
                receive(connection);
            } catch (SQLException exception) {
                if (running && !warned) {
                    log.warn("Execution event notices stopped; streams read every second until they return",
                            exception);
                    warned = true;
                }
            } finally {
                listening = false;
            }
            if (!pause()) {
                return;
            }
        }
    }

    private void receive(Connection connection) throws SQLException {
        PGConnection notices = connection.unwrap(PGConnection.class);
        int silent = 0;
        while (running) {
            PGNotification[] received = notices.getNotifications(RECEIVE_TIMEOUT_MILLIS);
            if (received == null || received.length == 0) {
                if (++silent % CHECK_AFTER_SILENT_RECEIVES == 0 && !connection.isValid(1)) {
                    throw new SQLException("The notice connection is no longer valid");
                }
                continue;
            }
            silent = 0;
            for (PGNotification notice : received) {
                wake(notice.getParameter());
            }
        }
    }

    private Connection connect() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", database.getUsername());
        if (database.getPassword() != null) {
            properties.setProperty("password", database.getPassword());
        }
        properties.setProperty("ApplicationName", APPLICATION_NAME);
        return DriverManager.getConnection(database.getJdbcUrl(), properties);
    }

    // The trigger sends the execution id; any other text on the channel is not ours and is ignored.
    private void wake(String executionId) {
        Long id;
        try {
            id = Long.valueOf(executionId);
        } catch (NumberFormatException _) {
            return;
        }
        lock.lock();
        try {
            Watch watch = watches.get(id);
            if (watch != null) {
                watch.notices++;
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    // Every stream reads again now: after the listener (re)connects, or when the backend shuts down.
    public void wakeEveryStream() {
        lock.lock();
        try {
            epoch++;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(RETRY_PAUSE);
            return true;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static final class Watch {

        private int streams;
        private long notices;
    }
}
