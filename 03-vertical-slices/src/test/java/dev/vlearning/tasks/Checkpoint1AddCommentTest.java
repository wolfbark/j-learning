package dev.vlearning.tasks;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * Acceptance test for the "add comment to task" feature you implement in step 1 —
 * deliberately in the OLD layered style. While you make this green, count every
 * package you have to touch. Write the number down; you will compare it in step 4.
 */
@Disabled("Checkpoint 1 — enable when you start step 1")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Checkpoint1AddCommentTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void addingACommentReturns201WithTheComment() throws Exception {
        long taskId = createTask();

        mockMvc.perform(post("/tasks/{id}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\": \"kriss\", \"text\": \"Blocked on the API team\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.author").value("kriss"))
                .andExpect(jsonPath("$.text").value("Blocked on the API team"));
    }

    @Test
    void commentsAreListedInInsertionOrder() throws Exception {
        long taskId = createTask();
        addComment(taskId, "kriss", "first");
        addComment(taskId, "sam", "second");

        mockMvc.perform(get("/tasks/{id}/comments", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].author").value("kriss"))
                .andExpect(jsonPath("$[0].text").value("first"))
                .andExpect(jsonPath("$[1].author").value("sam"))
                .andExpect(jsonPath("$[1].text").value("second"));
    }

    @Test
    void commentingOnUnknownTaskReturns404() throws Exception {
        mockMvc.perform(post("/tasks/{id}/comments", 999_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\": \"kriss\", \"text\": \"hello?\"}"))
                .andExpect(status().isNotFound());
    }

    private long createTask() throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Task with comments", "dueDate": "%s"}
                                """.formatted(LocalDate.now().plusDays(3))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void addComment(long taskId, String author, String text) throws Exception {
        mockMvc.perform(post("/tasks/{id}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\": \"%s\", \"text\": \"%s\"}".formatted(author, text)))
                .andExpect(status().isCreated());
    }
}
