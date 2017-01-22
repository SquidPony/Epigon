package squidpony.epigon.data.interfaces;

import java.util.Set;
import squidpony.epigon.data.generic.Ability;
import squidpony.epigon.data.generic.Skill;
import squidpony.epigon.data.interfaceBlueprints.CreatureBlueprint;
import squidpony.epigon.data.specific.Body;
import squidpony.epigon.universe.Rating;
import squidpony.squidmath.OrderedMap;

/**
 * A specific creature in the world.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class Creature {

    public CreatureBlueprint parent;
    public Body body;
    public OrderedMap<Skill, Rating> skills;
    public Set<Ability> abilities;
    public Ability defaultAttack;
    public boolean aware;//has noticed the player

    public Ability getDefaultAttack() {
        if (defaultAttack == null) {
            defaultAttack = abilities.stream().filter(a -> a.isAttack()).findAny().orElse(null);
        }

        return defaultAttack;
    }

    public void setDefaultAttack(Ability defaultAttack) {
        this.defaultAttack = defaultAttack;
    }

}
