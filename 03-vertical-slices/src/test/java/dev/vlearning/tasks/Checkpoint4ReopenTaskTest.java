package dev.vlearning.tasks;

import static org.hamcrest.Matchers.nullValue;
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
 * Acceptance test for the "reopen task" feature you implement in step 4 — this time
 * in the NEW sliced style. The whole feature must live in features/reopentask/.
 * Compare the number of directories you touch with the number from checkpoint 1.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Checkpoint4ReopenTaskTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void reopeningACompletedTaskMakesItOpenAgain() throws Exception {
        long id = createTask();
        mockMvc.perform(post("/tasks/{id}/complete", id)).andExpect(status().isOk());

        mockMvc.perform(post("/tasks/{id}/reopen", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.completedAt", nullValue()));
    }

    @Test
    void reopeningUnknownTaskReturns404() throws Exception {
        mockMvc.perform(post("/tasks/{id}/reopen", 999_999))
                .andExpect(status().isNotFound());
    }

    private long createTask() throws Exception {
        String body = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Reopen me", "dueDate": "%s"}
                                """.formatted(LocalDate.now().plusDays(3))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
