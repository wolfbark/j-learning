package dev.vlearning.tasks;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * Pins the current HTTP behavior of the app. These tests are enabled and must stay
 * green through every refactoring step. They know nothing about packages, services,
 * or repositories — only about the HTTP contract. That is what makes them the safety
 * net for the layers-to-slices refactoring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskApiBehaviorTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createTaskReturns201WithOpenTask() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Write the report", "description": "Q3 numbers", "dueDate": "%s"}
                                """.formatted(LocalDate.now().plusDays(7))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.title").value("Write the report"))
                .andExpect(jsonPath("$.description").value("Q3 numbers"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignee", nullValue()))
                .andExpect(jsonPath("$.completedAt", nullValue()));
    }

    @Test
    void createTaskWithBlankTitleIsRejected() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"  \"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void completeTaskSetsStatusDoneAndCompletionTimestamp() throws Exception {
        long id = createTask("Ship it", LocalDate.now().plusDays(1));

        mockMvc.perform(post("/tasks/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt", notNullValue()));
    }

    @Test
    void completingUnknownTaskReturns404() throws Exception {
        mockMvc.perform(post("/tasks/{id}/complete", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignTaskSetsAssignee() throws Exception {
        long id = createTask("Review PR", LocalDate.now().plusDays(2));

        mockMvc.perform(post("/tasks/{id}/assign", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\": \"kriss\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.assignee").value("kriss"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void assigningUnknownTaskReturns404() throws Exception {
        mockMvc.perform(post("/tasks/{id}/assign", 999_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\": \"kriss\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void overdueReportListsOnlyOpenTasksWithPastDueDate() throws Exception {
        long overdueId = createTask("Overdue and open", LocalDate.now().minusDays(3));
        createTask("Due next week", LocalDate.now().plusDays(7));
        long doneId = createTask("Overdue but done", LocalDate.now().minusDays(5));
        mockMvc.perform(post("/tasks/{id}/complete", doneId)).andExpect(status().isOk());
        mockMvc.perform(post("/tasks/{id}/assign", overdueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\": \"kriss\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reports/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOverdue").value(1))
                .andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.tasks[0].id").value(overdueId))
                .andExpect(jsonPath("$.tasks[0].title").value("Overdue and open"))
                .andExpect(jsonPath("$.tasks[0].assignee").value("kriss"))
                .andExpect(jsonPath("$.tasks[0].daysOverdue", greaterThanOrEqualTo(3)));
    }

    private long createTask(String title, LocalDate dueDate) throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "dueDate": "%s"}
                                """.formatted(title, dueDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
