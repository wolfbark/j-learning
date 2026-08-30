package dev.vlearning.airag.ingest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.vlearning.airag.RagProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Reads the corpus. Note what is *not* here: no chunking, no embedding, no model. Loading bytes is
 * the least interesting part of RAG and the part everyone gets right.
 */
@Component
public class CorpusLoader {

    private final RagProperties properties;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public CorpusLoader(RagProperties properties) {
        this.properties = properties;
    }

    public List<SourceDocument> load() {
        var documents = new ArrayList<SourceDocument>();
        for (String pattern : properties.corpusPaths()) {
            try {
                for (Resource resource : resolver.getResources(pattern)) {
                    if (!resource.isReadable()) {
                        continue;
                    }
                    documents.add(new SourceDocument(nameOf(resource),
                            new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8)));
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException("Cannot read corpus pattern " + pattern, e);
            }
        }
        documents.sort(Comparator.comparing(SourceDocument::name));
        return documents;
    }

    /**
     * Sixteen files called README.md are indistinguishable in a citation, so a README keeps its
     * directory name: {@code 09-event-sourcing/README.md}.
     */
    private static String nameOf(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) {
            return resource.getDescription();
        }
        try {
            if (filename.equalsIgnoreCase("README.md")) {
                var path = resource.getFile().toPath().toAbsolutePath().normalize();
                return path.getParent().getFileName() + "/" + filename;
            }
        }
        catch (IOException ignored) {
            // not a filesystem resource (a classpath entry in tests) — the bare filename is unique
        }
        return filename;
    }
}
