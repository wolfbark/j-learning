package dev.vlearning.tasks.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.vlearning.tasks.dto.AssignTaskRequest;
import dev.vlearning.tasks.dto.CreateTaskRequest;
import dev.vlearning.tasks.dto.OverdueReportResponse;
import dev.vlearning.tasks.dto.TaskResponse;
import dev.vlearning.tasks.service.TaskService;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @PostMapping("/tasks/{id}/complete")
    public TaskResponse complete(@PathVariable Long id) {
        return taskService.completeTask(id);
    }

    @PostMapping("/tasks/{id}/assign")
    public TaskResponse assign(@PathVariable Long id, @RequestBody AssignTaskRequest request) {
        return taskService.assignTask(id, request);
    }

    @GetMapping("/reports/overdue")
    public OverdueReportResponse overdueReport() {
        return taskService.overdueReport();
    }
}
