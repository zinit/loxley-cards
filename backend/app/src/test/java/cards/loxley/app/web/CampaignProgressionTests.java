package cards.loxley.app.web;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import cards.loxley.db.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CampaignProgressionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String registerAndGetJwt(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return setCookie.substring(setCookie.indexOf("jwt=") + 4, setCookie.indexOf(";"));
    }

    private String loginAndGetJwt(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        return setCookie.substring(setCookie.indexOf("jwt=") + 4, setCookie.indexOf(";"));
    }

    /**
     * Plays a full game on the given stage by executing legal moves until match ends.
     * Strategy: play first available non-PASS move, fall back to PASS.
     * Returns the parsed JSON of the final game state.
     */
    private JsonNode playGameToCompletion(String jwt, int stageNumber) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/games")
                        .cookie(new Cookie("jwt", jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":" + stageNumber + "}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode state = objectMapper.readTree(createResult.getResponse().getContentAsString());
        int safety = 0;

        while (!state.get("matchEnded").asBoolean()) {
            if (++safety > 500) {
                fail("Game did not end within 500 moves");
            }

            if (!state.get("yourTurn").asBoolean()) {
                fail("Stuck: not your turn and match not ended");
            }

            JsonNode legalMoves = state.get("legalMoves");
            JsonNode chosenMove = null;

            // Prefer non-PASS moves to actually play cards
            for (JsonNode move : legalMoves) {
                if (!"PASS".equals(move.get("kind").asText())) {
                    chosenMove = move;
                    break;
                }
            }
            // Fall back to PASS
            if (chosenMove == null) {
                chosenMove = legalMoves.get(0);
            }

            String moveJson = buildMoveJson(chosenMove);
            String gameId = state.get("gameId").asText();

            MvcResult moveResult = mockMvc.perform(post("/api/games/{id}/moves", gameId)
                            .cookie(new Cookie("jwt", jwt))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(moveJson))
                    .andExpect(status().isOk())
                    .andReturn();

            state = objectMapper.readTree(moveResult.getResponse().getContentAsString());
        }

        return state;
    }

    private String buildMoveJson(JsonNode move) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"kind\":\"").append(move.get("kind").asText()).append("\"");

        if (move.has("handInstanceId") && !move.get("handInstanceId").isNull()) {
            sb.append(",\"handInstanceId\":\"").append(move.get("handInstanceId").asText()).append("\"");
        }
        if (move.has("targetRow") && !move.get("targetRow").isNull()) {
            sb.append(",\"targetRow\":\"").append(move.get("targetRow").asText()).append("\"");
        }
        if (move.has("targetInstanceId") && !move.get("targetInstanceId").isNull()) {
            sb.append(",\"targetInstanceId\":\"").append(move.get("targetInstanceId").asText()).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    @Test
    void authMe_newUser_returnsHighestUnlockedStage1() throws Exception {
        String jwt = registerAndGetJwt("campaign_new", "sherwood1");

        mockMvc.perform(get("/auth/me")
                        .cookie(new Cookie("jwt", jwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("campaign_new"))
                .andExpect(jsonPath("$.highestUnlockedStage").value(1));
    }

    @Test
    void authMe_contractShape_matchesExpectedFields() throws Exception {
        String jwt = registerAndGetJwt("campaign_shape", "sherwood1");

        MvcResult result = mockMvc.perform(get("/auth/me")
                        .cookie(new Cookie("jwt", jwt)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, body.size(), "AuthResponse should have exactly 2 fields");
        assertTrue(body.has("username"), "Must have 'username' field");
        assertTrue(body.has("highestUnlockedStage"), "Must have 'highestUnlockedStage' field");
        assertTrue(body.get("username").isTextual(), "'username' must be a string");
        assertTrue(body.get("highestUnlockedStage").isInt(), "'highestUnlockedStage' must be an integer");
    }

    @Test
    void winStage1_freshSession_progressPersisted() throws Exception {
        String jwt = registerAndGetJwt("campaign_persist", "sherwood1");

        // Play stage 1 — retry up to 10 times in case P1 loses (ultra_easy RandomBot)
        boolean won = false;
        for (int attempt = 0; attempt < 10; attempt++) {
            JsonNode finalState = playGameToCompletion(jwt, 1);
            String winner = finalState.has("matchWinner") && !finalState.get("matchWinner").isNull()
                    ? finalState.get("matchWinner").asText() : null;

            if ("P1".equals(winner)) {
                assertNotNull(finalState.get("newHighestUnlockedStage"),
                        "Winning response must include newHighestUnlockedStage");
                assertEquals(2, finalState.get("newHighestUnlockedStage").asInt(),
                        "Winning stage 1 should unlock stage 2");
                won = true;
                break;
            }
        }
        assertTrue(won, "P1 should win at least once in 10 attempts against ultra_easy bot");

        // Logout
        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("jwt", jwt)))
                .andExpect(status().isOk());

        // Login again — fresh session
        String freshJwt = loginAndGetJwt("campaign_persist", "sherwood1");

        // Verify progress persisted across sessions
        mockMvc.perform(get("/auth/me")
                        .cookie(new Cookie("jwt", freshJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highestUnlockedStage").value(2));
    }

    @Test
    void createGame_lockedStage_returns400() throws Exception {
        String jwt = registerAndGetJwt("campaign_locked", "sherwood1");

        // Stage 2 is locked for a new user (highestUnlockedStage = 1)
        mockMvc.perform(post("/api/games")
                        .cookie(new Cookie("jwt", jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":2}"))
                .andExpect(status().isBadRequest());

        // Stage 1 is unlocked
        mockMvc.perform(post("/api/games")
                        .cookie(new Cookie("jwt", jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void createGame_replayCompletedStage_allowed() throws Exception {
        String jwt = registerAndGetJwt("campaign_replay", "sherwood1");

        // Manually advance user to stage 3
        var user = userRepository.findByUsername("campaign_replay").orElseThrow();
        user.setHighestUnlockedStage(3);
        userRepository.save(user);

        // Replaying stage 1 (completed) should be allowed
        mockMvc.perform(post("/api/games")
                        .cookie(new Cookie("jwt", jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":1}"))
                .andExpect(status().isOk());
    }
}
