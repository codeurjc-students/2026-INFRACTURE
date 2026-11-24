package es.codeurjc.infracture.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComposeUsageTests {

    @Test
    void readsCpuAndMemoryAsDockerPrintsThem() {
        assertThat(ComposeRuntimeEngine.parseUsage("49.60%", "572KiB / 384MiB"))
                .isEqualTo(new ResourceUsage(49.6, 585_728L));
        assertThat(ComposeRuntimeEngine.parseUsage("150.25%", "1.5GiB / 2GiB"))
                .isEqualTo(new ResourceUsage(150.25, 1_610_612_736L));
        assertThat(ComposeRuntimeEngine.parseUsage("1.00%", "512B / 384MiB"))
                .isEqualTo(new ResourceUsage(1.0, 512L));
    }

    @Test
    void keepsARealZeroOfARunningContainer() {
        assertThat(ComposeRuntimeEngine.parseUsage("0.00%", "1MiB / 384MiB"))
                .isEqualTo(new ResourceUsage(0.0, 1_048_576L));
    }

    // Docker prints 0.00% and 0B / 0B for a container that is not running: that is no reading, not zero.
    @Test
    void treatsAContainerThatIsNotRunningAsAbsent() {
        assertThat(ComposeRuntimeEngine.parseUsage("0.00%", "0B / 0B")).isEqualTo(new ResourceUsage(null, null));
    }

    @Test
    void keepsTheValueThatWasReadWhenTheOtherIsMissing() {
        assertThat(ComposeRuntimeEngine.parseUsage("--", "-- / --")).isEqualTo(new ResourceUsage(null, null));
        assertThat(ComposeRuntimeEngine.parseUsage("12.50%", "-- / --")).isEqualTo(new ResourceUsage(12.5, null));
        assertThat(ComposeRuntimeEngine.parseUsage("--", "2MiB / 384MiB"))
                .isEqualTo(new ResourceUsage(null, 2_097_152L));
    }
}
