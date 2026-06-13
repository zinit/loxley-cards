package cards.loxley.app.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class GameControllerTests {

    @Autowired
    private MockMvc mockMvc;

    private String createGameAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":1}"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Extract gameId from JSON — simple substring approach to avoid extra dependency
        int start = body.indexOf("\"gameId\":\"") + "\"gameId\":\"".length();
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }

    @Test
    void createGame_validStage_returnsGameState() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").isNotEmpty())
                .andExpect(jsonPath("$.roundNumber").value(1))
                .andExpect(jsonPath("$.you.hand").isNotEmpty())
                .andExpect(jsonPath("$.opponent.handSize").isNumber())
                .andExpect(jsonPath("$.legalMoves").isNotEmpty());
    }

    @Test
    void createGame_invalidStage_returns400() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void getState_existingGame_returnsState() throws Exception {
        String gameId = createGameAndGetId();

        mockMvc.perform(get("/api/games/{id}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.roundNumber").isNumber());
    }

    @Test
    void getState_unknownGame_returns404() throws Exception {
        mockMvc.perform(get("/api/games/{id}", "nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void makeMove_pass_returnsUpdatedState() throws Exception {
        String gameId = createGameAndGetId();

        mockMvc.perform(post("/api/games/{id}/moves", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PASS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId));
    }

    @Test
    void makeMove_illegalMove_returns400() throws Exception {
        String gameId = createGameAndGetId();

        mockMvc.perform(post("/api/games/{id}/moves", gameId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"UNIT\",\"handInstanceId\":\"fake-id\",\"targetRow\":\"CLOSE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_MOVE"));
    }

    @Test
    void makeMove_unknownGame_returns404() throws Exception {
        mockMvc.perform(post("/api/games/{id}/moves", "nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PASS\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void opponentHandHidden() throws Exception {
        mockMvc.perform(post("/api/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stageNumber\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opponent.handSize").isNumber())
                .andExpect(jsonPath("$.opponent.hand").doesNotExist());
    }
}
