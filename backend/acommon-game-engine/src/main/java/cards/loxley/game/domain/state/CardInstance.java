package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.Card;

import java.util.Objects;
import java.util.UUID;

public final class CardInstance {

    private final String instanceId;
    private final Card card;
    private final Player owner;

    public CardInstance(Card card, Player owner) {
        this.instanceId = UUID.randomUUID().toString();
        this.card = Objects.requireNonNull(card, "card");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public String instanceId() {
        return instanceId;
    }

    public Card card() {
        return card;
    }

    public Player owner() {
        return owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CardInstance other)) return false;
        return instanceId.equals(other.instanceId);
    }

    @Override
    public int hashCode() {
        return instanceId.hashCode();
    }

    @Override
    public String toString() {
        return card.id() + "#" + instanceId.substring(0, 8);
    }
}
