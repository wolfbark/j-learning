package dev.vlearning.airag.ingest;

/** A raw corpus file: {@code name} is what a citation will point at. */
public record SourceDocument(String name, String markdown) {
}
