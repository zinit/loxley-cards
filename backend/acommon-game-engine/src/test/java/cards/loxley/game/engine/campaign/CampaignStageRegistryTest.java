package cards.loxley.game.engine.campaign;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignStageRegistryTest {

    @Autowired
    CampaignStageRegistry registry;

    @Test
    void allTenStagesPresent() {
        List<CampaignStage> stages = registry.all();
        assertThat(stages).hasSize(10);
        assertThat(stages.stream().map(CampaignStage::stageNumber))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void stageToProfileMappingMatchesSpec() {
        Map<Integer, String> expected = Map.of(
                1, "ultra_easy",
                2, "easy",
                3, "easy",
                4, "medium",
                5, "medium",
                6, "medium",
                7, "hard",
                8, "hard",
                9, "hard",
                10, "top_hard"
        );
        for (Map.Entry<Integer, String> entry : expected.entrySet()) {
            CampaignStage stage = registry.findByNumber(entry.getKey()).orElseThrow();
            assertThat(stage.opponentProfileId())
                    .as("stage %d → profile", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }
}
