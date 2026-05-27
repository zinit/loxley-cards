package cards.loxley.game.domain.state;

import cards.loxley.game.domain.card.RowId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class RowState {

    private final RowId rowId;
    private final List<CardInstance> units = new ArrayList<>();
    private boolean weatherActive = false;
    private boolean hornActive = false;

    public RowState(RowId rowId) {
        this.rowId = rowId;
    }

    public RowId rowId() {
        return rowId;
    }

    public List<CardInstance> units() {
        return Collections.unmodifiableList(units);
    }

    public boolean weatherActive() {
        return weatherActive;
    }

    public boolean hornActive() {
        return hornActive;
    }

    public void addUnit(CardInstance ci) {
        units.add(ci);
    }

    public boolean removeUnit(String instanceId) {
        Iterator<CardInstance> it = units.iterator();
        while (it.hasNext()) {
            if (it.next().instanceId().equals(instanceId)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public Optional<CardInstance> findUnit(String instanceId) {
        for (CardInstance ci : units) {
            if (ci.instanceId().equals(instanceId)) {
                return Optional.of(ci);
            }
        }
        return Optional.empty();
    }

    public void applyWeather() {
        this.weatherActive = true;
    }

    public void removeWeather() {
        this.weatherActive = false;
    }

    public void applyHorn() {
        this.hornActive = true;
    }

    public void removeHorn() {
        this.hornActive = false;
    }

    public void clear() {
        units.clear();
        weatherActive = false;
        hornActive = false;
    }

    public int unitCount() {
        return units.size();
    }
}
