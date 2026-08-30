package dev.vlearning.tasks.dto;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDate dueDate,
        String status,
        String assignee,
        Instant completedAt) {
}
