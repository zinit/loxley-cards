package cards.loxley.game.engine.campaign;

public record CampaignStage(
        int stageNumber,
        String opponentProfileId,
        String description
) {
}
