package squidpony.epigon.data.mixin;

import squidpony.epigon.data.generic.Ability;
import squidpony.epigon.data.generic.Skill;
import squidpony.epigon.universe.Rating;
import squidpony.squidmath.OrderedMap;

import java.util.HashSet;
import java.util.Set;

/**
 * A specific creature in the world.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class Creature {

    public Creature parent;
    public OrderedMap<Skill, Rating> skills = new OrderedMap<>();
    public OrderedMap<Skill, Rating> skillProgression = new OrderedMap<>();
    public Set<Ability> abilities = new HashSet<>();
    public EquippedData equippedData;

    public OrderedMap<Profession, Rating> professions = new OrderedMap<>();

    // Runtime values
    public boolean aware;//has noticed the player

    /*
     * Properties Creatures Need to model real life
     * 
     * taxonomy
     * habitation areas (with frequency / likelihood)
     * behavior
     * size of territory
     * breeding method
     * gestation time
     * number of children
     * years to maturity
     * lifespan
     * food type (and specific animals / plants preferred)
     * needed food intake per day
     */
}
