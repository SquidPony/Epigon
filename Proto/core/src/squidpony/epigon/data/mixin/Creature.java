package squidpony.epigon.data.mixin;

import java.util.Set;
import squidpony.epigon.data.generic.Ability;
import squidpony.epigon.data.generic.Profession;
import squidpony.epigon.data.generic.Skill;
import squidpony.epigon.universe.Rating;
import squidpony.squidmath.OrderedMap;

/**
 * A specific creature in the world.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class Creature {

    public Creature parent;
    public Profession profession;
    public OrderedMap<Skill, Rating> skills;
    public Set<Ability> abilities;

    public boolean aware;//has noticed the player

    /*
     * Properties Creatures Need to model real life
     * 
     * taxonomy
     * habitation areas (with frequency / likelyhood)
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