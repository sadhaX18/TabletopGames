package games.monopolydeal.actions.informationcontainer;

import core.AbstractGameState;
import core.actions.AbstractAction;
import core.components.Component;
import games.monopolydeal.cards.PropertySet;
import games.monopolydeal.cards.SetType;

import java.util.Objects;

/**
 * <p>A simple action which does not execute any command but acts as an information container for other EAS.</p>
 */
public class RentOf extends AbstractAction {
    final int pSetId;
    final SetType pSetType;
    public int rent;

    public RentOf(int pSetId, SetType pSetType, int rent){
        this.pSetId = pSetId;
        this.pSetType = pSetType;
        this.rent = rent;
    }
    public RentOf(PropertySet pSet) {
        this.pSetId = pSet.getComponentID();
        this.pSetType = pSet.getSetType();
        if(pSet.isComplete){
            rent = pSetType.rent[pSetType.setSize-1];
            if(pSet.hasHouse) rent = rent + 3;
            if(pSet.hasHotel) rent = rent + 4;
        } else {
            if(pSet.getPropertySetSize()-1 >= pSetType.rent.length)
                throw new AssertionError("Another thing which should not happen");
            if(pSet.getPropertySetSize()==0)
                rent = 0;
            else rent = pSetType.rent[pSet.getPropertySetSize() - 1];
        }
    }
    @Override
    public boolean execute(AbstractGameState gs) {
        return true;
    }
    @Override
    public RentOf copy() {
        return new RentOf(pSetId, pSetType, rent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RentOf)) return false;
        RentOf rentOf = (RentOf) o;
        return pSetId == rentOf.pSetId && rent == rentOf.rent && pSetType == rentOf.pSetType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pSetId, pSetType, rent);
    }

    @Override
    public String toString() {
        return pSetType + " rent is " + rent;
    }
    @Override
    public String getString(AbstractGameState gameState) {
        return toString();
    }
}
