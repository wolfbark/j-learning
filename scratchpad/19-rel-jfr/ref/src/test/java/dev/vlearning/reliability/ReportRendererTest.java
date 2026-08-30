package dev.vlearning.reliability;

import dev.vlearning.reliability.chaos.ChaosSwitch;
import dev.vlearning.reliability.profiling.ReportRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Always on. Step 5 diagnoses this class; any fix has to keep rendering the same
 * report.
 */
class ReportRendererTest {

    @Test
    @DisplayName("rendering is deterministic for a given report id")
    void deterministic() {
        var renderer = new ReportRenderer(new ChaosSwitch());

        String first = new String(renderer.render("R-77"));
        String second = new String(renderer.render("R-77"));

        assertThat(first).isEqualTo(second).contains("REPORT R-77");
        assertThat(renderer.renderCount()).isEqualTo(2);
    }
}
