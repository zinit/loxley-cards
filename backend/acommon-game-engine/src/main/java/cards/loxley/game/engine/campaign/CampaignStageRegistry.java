package cards.loxley.game.engine.campaign;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CampaignStageRegistry {

    private static final String RESOURCE = "data/campaign_stages.json";

    private final Map<Integer, CampaignStage> stagesByNumber;

    public CampaignStageRegistry(ObjectMapper objectMapper) {
        ObjectMapper lenient = objectMapper
                .rebuild()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
        List<CampaignStage> stages = load(lenient);
        Map<Integer, CampaignStage> map = new LinkedHashMap<>();
        for (CampaignStage stage : stages) {
            map.put(stage.stageNumber(), stage);
        }
        this.stagesByNumber = Map.copyOf(map);
    }

    private List<CampaignStage> load(ObjectMapper mapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream is = resource.getInputStream()) {
            return mapper.readValue(is, new TypeReference<List<CampaignStage>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load campaign stages from " + RESOURCE, e);
        }
    }

    public Optional<CampaignStage> findByNumber(int stageNumber) {
        return Optional.ofNullable(stagesByNumber.get(stageNumber));
    }

    public List<CampaignStage> all() {
        return stagesByNumber.values().stream()
                .sorted(java.util.Comparator.comparingInt(CampaignStage::stageNumber))
                .toList();
    }
}
