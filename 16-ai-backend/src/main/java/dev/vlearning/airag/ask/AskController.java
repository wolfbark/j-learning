package dev.vlearning.airag.ask;

import java.util.List;

import dev.vlearning.airag.ingest.IngestionReport;
import dev.vlearning.airag.ingest.IngestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private final RagService ragService;
    private final IngestionService ingestionService;

    public AskController(RagService ragService, IngestionService ingestionService) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
    }

    public record AskRequest(String question) {
    }

    @PostMapping("/ask")
    Answer ask(@RequestBody AskRequest request) {
        return ragService.ask(request.question());
    }

    /** Retrieval without generation — the endpoint you will actually debug with. */
    @GetMapping("/retrieve")
    List<Passage> retrieve(@RequestParam String question) {
        return ragService.retrieve(question);
    }

    @PostMapping("/admin/ingest")
    IngestionReport ingest() {
        return ingestionService.ingestAll();
    }
}
