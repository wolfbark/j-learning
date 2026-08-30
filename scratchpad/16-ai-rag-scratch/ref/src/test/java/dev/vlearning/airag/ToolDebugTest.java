package dev.vlearning.airag;

import dev.vlearning.airag.support.PgVectorTestBase;
import dev.vlearning.airag.support.TestModels;
import dev.vlearning.airag.tools.LessonIndex;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestModels.class)
class ToolDebugTest extends PgVectorTestBase {

    @Autowired
    ChatClient chatClient;

    @Autowired
    LessonIndex lessonIndex;

    @Test
    void debug() {
        lessonIndex.resetInvocationCount();
        scriptedChatModel.scriptToolCall("whichLessonCovers", "{\"topic\": \"virtual threads\"}")
                .scriptQuotingToolResults("The lesson index says: ");
        var response = chatClient.prompt().system("sys").user("Which lesson covers virtual threads?")
                .tools(lessonIndex).call().chatResponse();
        System.out.println("DBG prompts=" + scriptedChatModel.promptCount()
                + " toolCalls=" + lessonIndex.invocationCount()
                + " content=[" + response.getResult().getOutput().getText() + "]"
                + " hasToolCalls=" + response.hasToolCalls());
        scriptedChatModel.prompts().forEach(p -> System.out.println("DBG PROMPT ---- " + p.getInstructions().size() + " msgs: "
                + p.getInstructions().stream().map(m -> m.getMessageType() + ":" + m.getText()).toList()));
    }
}
