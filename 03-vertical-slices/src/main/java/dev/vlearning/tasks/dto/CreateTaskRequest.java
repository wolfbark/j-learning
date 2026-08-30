package dev.vlearning.tasks.dto;

import java.time.LocalDate;

public record CreateTaskRequest(String title, String description, LocalDate dueDate) {
}
