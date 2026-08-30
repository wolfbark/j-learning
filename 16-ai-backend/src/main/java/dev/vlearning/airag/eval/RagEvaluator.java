package dev.vlearning.airag.eval;

import java.util.List;

import dev.vlearning.airag.ask.RagService;
import org.springframework.stereotype.Component;

/**
 * The offline eval harness: run a golden set through the real pipeline and count how often the
 * expected citation came back. Twenty lines, no model bill, and the only honest way to answer
 * "did that chunking change help?".
 *
 * <p>Step 6: implement {@link #evaluate(List)}.
 */
@Component
public class RagEvaluator {

    private final RagService ragService;

    public RagEvaluator(RagService ragService) {
        this.ragService = ragService;
    }

    public EvalReport evaluate(List<EvalCase> cases) {
        throw new UnsupportedOperationException("Step 6 — score the golden set");
    }

    protected RagService ragService() {
        return this.ragService;
    }
}
