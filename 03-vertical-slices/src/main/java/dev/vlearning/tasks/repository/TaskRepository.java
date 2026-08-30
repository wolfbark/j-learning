package dev.vlearning.tasks.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.vlearning.tasks.model.Task;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatusAndDueDateBeforeOrderByDueDateAsc(Task.Status status, LocalDate date);
}
