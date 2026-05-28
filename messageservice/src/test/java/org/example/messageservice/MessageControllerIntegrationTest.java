package org.example.messageservice;

import org.example.messageservice.model.MessageEntity;
import org.example.messageservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the authz contract of MessageController: read isolation by JWT subject (now a
 * user UUID), write authorship pinned to the JWT subject, and the bot's special ability
 * to write to another user's thread via {@code X-Conversation-Owner}.
 */
class MessageControllerIntegrationTest extends AbstractIntegrationTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MessageRepository messageRepository;

    @Value("${app.bot.user-id}")
    UUID botUserId;

    @BeforeEach
    void cleanSlate() {
        messageRepository.deleteAll();
        mockUserProfilesEmpty();
    }

    @Test
    void getMessages_returnsOnlyThreadsOwnedByCaller() throws Exception {
        seedMessage(ALICE, ALICE, "hello from alice");
        seedMessage(botUserId, ALICE, "bot reply to alice");
        seedMessage(BOB, BOB, "hello from bob");
        seedMessage(botUserId, BOB, "bot reply to bob");

        // Alice sees her own posts AND the bot's reply addressed to her — but never Bob's
        // thread, even though all four rows live in the same table. The bot row shows up
        // with username "bot" because messageservice substitutes the display name for
        // the reserved bot UUID rather than dialing userservice for it.
        mockMvc.perform(get("/messages").with(jwt().jwt(jwtFor(ALICE.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].content",
                        org.hamcrest.Matchers.containsInAnyOrder("hello from alice", "bot reply to alice")));
    }

    @Test
    void postMessage_byUser_ignoresXConversationOwnerHeader() throws Exception {
        // A normal user trying to plant a message in someone else's thread must be silently
        // pinned to their own — otherwise any logged-in user could write into any chat.
        mockMvc.perform(post("/messages")
                        .with(jwt().jwt(jwtFor(ALICE.toString())))
                        .header("X-Conversation-Owner", BOB.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"sneaky\"}"))
                .andExpect(status().isOk());

        List<MessageEntity> rows = messageRepository.findAll();
        assertThat(rows).hasSize(1);
        MessageEntity row = rows.getFirst();
        assertThat(row.getUserId()).isEqualTo(ALICE);
        assertThat(row.getOwnerUserId())
                .as("owner must be pinned to the JWT subject for non-bot callers")
                .isEqualTo(ALICE);
    }

    @Test
    void postMessage_byBot_honorsXConversationOwnerHeader() throws Exception {
        // The bot uses client_credentials (sub = reserved bot UUID) and must be able to
        // reply into the user's conversation; that's the only way users see bot replies
        // addressed to them.
        mockMvc.perform(post("/messages")
                        .with(jwt().jwt(jwtFor(botUserId.toString())))
                        .header("X-Conversation-Owner", ALICE.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi alice from bot\"}"))
                .andExpect(status().isOk());

        List<MessageEntity> rows = messageRepository.findAll();
        assertThat(rows).hasSize(1);
        MessageEntity row = rows.getFirst();
        assertThat(row.getUserId()).isEqualTo(botUserId);
        assertThat(row.getOwnerUserId()).isEqualTo(ALICE);
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/messages")).andExpect(status().isUnauthorized());
    }

    private MvcResult seedMessage(UUID author, UUID owner, String content) throws Exception {
        return mockMvc.perform(post("/messages")
                        .with(jwt().jwt(jwtFor(author.toString())))
                        // Only honored when author == bot UUID; ignored otherwise (pinned to author).
                        .header("X-Conversation-Owner", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }
}
