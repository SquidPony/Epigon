package squidpony.epigon.data.blueprints;

import java.util.ArrayList;
import java.util.HashMap;
import squidpony.epigon.data.EpiData;
import squidpony.epigon.data.generic.Element;
import squidpony.epigon.data.generic.Skill;
import squidpony.epigon.universe.Rating;
import squidpony.squidmath.ProbabilityTable;

/**
 * Base class for all classes that have physical properties in the world.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class PhysicalBlueprint extends EpiData {

    public ArrayList<String> possibleAliases = new ArrayList<>();
    public ProbabilityTable<ModificationBlueprint> possibleModifications = new ProbabilityTable<>();
    public HashMap<String, Float> passthroughResistances = new HashMap<>();
    /**
     * Conditions the item will start with on creation
     */
    public ProbabilityTable<ConditionBlueprint> conditions = new ProbabilityTable<>();
    /**
     * The list of physical objects it drops on destruction no matter what the
     * source
     */
    public ProbabilityTable<PhysicalBlueprint> drops = new ProbabilityTable<>();
    /**
     * A list of what the item might become when a given element is used on it.
     */
    public HashMap<Element, ProbabilityTable<ArrayList<PhysicalBlueprint>>> becomes = new HashMap<>();
    /**
     * If the given skill is possessed then a given string will be presented as
     * the identification. The description will be used if no matching skill is
     * available.
     */
    public HashMap<Skill, HashMap<Rating, String>> identification;
    public char symbol;
    /**
     * When marked generic the item won't be created in the world.
     */
    public boolean generic = false;
    /**
     * When marked as unique the item will only be created once at most per
     * world.
     */
    public boolean unique;
    public int rarity;
    public String destructionSound, idleSound, movementSound;
    public int destructionVolume, idleVolume, movementVolume;
}
