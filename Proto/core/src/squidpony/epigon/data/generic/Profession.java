package squidpony.epigon.data.generic;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import squidpony.squidmath.OrderedMap;

import squidpony.epigon.data.EpiData;
import squidpony.epigon.data.blueprint.ConditionBlueprint;
import squidpony.epigon.data.blueprint.PhysicalBlueprint;
import squidpony.epigon.data.blueprint.RecipeBlueprint;
import squidpony.epigon.universe.LiveValue;
import squidpony.epigon.universe.Stat;
import squidpony.epigon.universe.Rating;

/**
 * A grouping of starting skills and stats plus an aptitude towards increasing them, starting
 * equipment, recipes known, starting perks, starting conditions, and starting abilities.
 *
 * Creatures should have a list of what kinds of professions they can have. Modifications may change
 * their list.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class Profession extends EpiData {

    public OrderedMap<Skill, Rating> initialSkillRatings;
    public OrderedMap<Skill, Rating> skillProgress;
    public EnumMap<Stat, LiveValue> initialStatLevels = new EnumMap<>(Stat.class);
    public EnumMap<Stat, Rating> statProgress = new EnumMap<>(Stat.class);
    public List<PhysicalBlueprint> items;
    public Set<RecipeBlueprint> recipes;
    public Set<ConditionBlueprint> perks;
    public List<ConditionBlueprint> conditions;
    public Set<Ability> abilities;
}
