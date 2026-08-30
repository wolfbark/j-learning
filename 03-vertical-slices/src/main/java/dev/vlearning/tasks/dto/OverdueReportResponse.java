package dev.vlearning.tasks.dto;

import java.time.LocalDate;
import java.util.List;

public record OverdueReportResponse(int totalOverdue, List<OverdueTask> tasks) {

    public record OverdueTask(Long id, String title, String assignee, LocalDate dueDate, long daysOverdue) {
    }
}
