package dev.vlearning.airag.eval;

import java.util.ArrayList;
import java.util.List;

import dev.vlearning.airag.ask.Citation;
import dev.vlearning.airag.ask.RagService;
import org.springframework.stereotype.Component;

@Component
public class RagEvaluator {

    private final RagService ragService;

    public RagEvaluator(RagService ragService) {
        this.ragService = ragService;
    }

    public EvalReport evaluate(List<EvalCase> cases) {
        int passed = 0;
        var failures = new ArrayList<String>();
        for (EvalCase testCase : cases) {
            var answer = this.ragService.ask(testCase.question());
            boolean hit = answer.citations().stream().anyMatch(citation -> matches(citation, testCase));
            if (hit) {
                passed++;
            }
            else {
                failures.add("%s -> expected %s / '%s', cited %s".formatted(testCase.question(),
                        testCase.expectedSource(), testCase.expectedHeading(), answer.citations()));
            }
        }
        return new EvalReport(cases.size(), passed, List.copyOf(failures));
    }

    private static boolean matches(Citation citation, EvalCase testCase) {
        return citation.sourceFile().equals(testCase.expectedSource())
                && citation.heading().contains(testCase.expectedHeading());
    }

    protected RagService ragService() {
        return this.ragService;
    }
}
