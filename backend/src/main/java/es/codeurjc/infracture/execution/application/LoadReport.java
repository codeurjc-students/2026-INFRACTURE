package es.codeurjc.infracture.execution.application;

import es.codeurjc.infracture.execution.domain.LoadSummary;

// What the k6 container left: its exit code (null when it never started) and its summary (null when it
// printed none). k6 exits with 0 when its duration ends and with 105 when Stop interrupts it.
public record LoadReport(Integer exitCode, LoadSummary summary) {

    public boolean completed() {
        return Integer.valueOf(0).equals(exitCode);
    }
}
