package dev.vlearning.tasks.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.vlearning.tasks.dto.AssignTaskRequest;
import dev.vlearning.tasks.dto.CreateTaskRequest;
import dev.vlearning.tasks.dto.OverdueReportResponse;
import dev.vlearning.tasks.dto.TaskResponse;
import dev.vlearning.tasks.model.Task;
import dev.vlearning.tasks.repository.TaskRepository;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public TaskResponse createTask(CreateTaskRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Task task = new Task(request.title(), request.description(), request.dueDate());
        return toResponse(taskRepository.save(task));
    }

    public TaskResponse completeTask(Long id) {
        Task task = load(id);
        task.setStatus(Task.Status.DONE);
        if (task.getCompletedAt() == null) {
            task.setCompletedAt(Instant.now());
        }
        return toResponse(task);
    }

    public TaskResponse assignTask(Long id, AssignTaskRequest request) {
        Task task = load(id);
        task.setAssignee(request.assignee());
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public OverdueReportResponse overdueReport() {
        LocalDate today = LocalDate.now();
        List<Task> overdue = taskRepository
                .findByStatusAndDueDateBeforeOrderByDueDateAsc(Task.Status.OPEN, today);
        List<OverdueReportResponse.OverdueTask> rows = overdue.stream()
                .map(task -> new OverdueReportResponse.OverdueTask(
                        task.getId(),
                        task.getTitle(),
                        task.getAssignee(),
                        task.getDueDate(),
                        ChronoUnit.DAYS.between(task.getDueDate(), today)))
                .toList();
        return new OverdueReportResponse(rows.size(), rows);
    }

    private Task load(Long id) {
        return taskRepository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDueDate(),
                task.getStatus().name(),
                task.getAssignee(),
                task.getCompletedAt());
    }
}
