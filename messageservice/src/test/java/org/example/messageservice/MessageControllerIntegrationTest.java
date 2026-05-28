package org.example.messageservice;

import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the authz contract of MessageController: read isolation by JWT subject, write
 * authorship pinned to the JWT subject, and the bot's special ability to write to another
 * user's thread via {@code X-Conversation-Owner}.
 */
class MessageControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MessageRepository messageRepository;

    @BeforeEach
    void cleanSlate() {
        messageRepository.deleteAll();
        mockUserProfilesEmpty(List.of());
    }

    @Test
    void getMessages_returnsOnlyThreadsOwnedByCaller() throws Exception {
        seedMessage("alice", "alice", "hello from alice");
        seedMessage("bot", "alice", "bot reply to alice");
        seedMessage("bob", "bob", "hello from bob");
        seedMessage("bot", "bob", "bot reply to bob");

        // Alice sees her own posts AND the bot's reply addressed to her — but never Bob's
        // thread, even though all four rows live in the same table.
        mockMvc.perform(get("/messages").with(jwt().jwt(jwtFor("alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].username", org.hamcrest.Matchers.containsInAnyOrder("alice", "bot")))
                .andExpect(jsonPath("$[*].content",
                        org.hamcrest.Matchers.containsInAnyOrder("hello from alice", "bot reply to alice")));
    }

    @Test
    void postMessage_byUser_ignoresXConversationOwnerHeader() throws Exception {
        // A normal user trying to plant a message in someone else's thread must be silently
        // pinned to their own — otherwise any logged-in user could write into any chat.
        mockMvc.perform(post("/messages")
                        .with(jwt().jwt(jwtFor("alice")))
                        .header("X-Conversation-Owner", "bob")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"sneaky\"}"))
                .andExpect(status().isOk());

        List<MessageEntity> rows = messageRepository.findAll();
        assertThat(rows).hasSize(1);
        MessageEntity row = rows.getFirst();
        assertThat(row.getUsername()).isEqualTo("alice");
        assertThat(row.getOwnerUsername())
                .as("owner must be pinned to the JWT subject for non-bot callers")
                .isEqualTo("alice");
    }

    @Test
    void postMessage_byBot_honorsXConversationOwnerHeader() throws Exception {
        // The bot uses client_credentials (sub = "bot") and must be able to reply into the
        // user's conversation; that's the only way users see bot replies addressed to them.
        mockMvc.perform(post("/messages")
                        .with(jwt().jwt(jwtFor("bot")))
                        .header("X-Conversation-Owner", "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi alice from bot\"}"))
                .andExpect(status().isOk());

        List<MessageEntity> rows = messageRepository.findAll();
        assertThat(rows).hasSize(1);
        MessageEntity row = rows.getFirst();
        assertThat(row.getUsername()).isEqualTo("bot");
        assertThat(row.getOwnerUsername()).isEqualTo("alice");
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/messages")).andExpect(status().isUnauthorized());
    }

    private MvcResult seedMessage(String author, String owner, String content) throws Exception {
        return mockMvc.perform(post("/messages")
                        .with(jwt().jwt(jwtFor(author)))
                        // Only honored when author == "bot"; ignored otherwise (pinned to author).
                        .header("X-Conversation-Owner", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }
}
