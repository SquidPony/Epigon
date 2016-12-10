package squidpony.epigon.data.specific;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Stack;
import java.util.TreeMap;
import squidpony.epigon.data.Stat;
import squidpony.epigon.data.blueprints.CreatureBlueprint;
import squidpony.epigon.data.blueprints.ItemBlueprint;
import squidpony.epigon.data.generic.Ability;
import squidpony.epigon.data.generic.Skill;
import squidpony.epigon.universe.Rating;

/**
 * A specific creature in the world.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class Creature extends Physical{

    public CreatureBlueprint parent;
    public String knownName, trueName;
    public TreeMap<Skill, Rating> skills = new TreeMap<>();
    public ArrayList<Ability> abilities = new ArrayList<>();
    public EnumMap<Stat, Integer> baseStats = new EnumMap<>(Stat.class);
    public EnumMap<Stat, Integer> currentStats = new EnumMap<>(Stat.class);
    public Ability defaultAttack;
    public Stack<Item> inventory;
    public boolean aware;//has noticed the player

    public Ability getDefaultAttack() {
        //if there is an attack and no default is yet set, arbitrarily pick the first ability with an attack as the default
        if (defaultAttack == null && !abilities.isEmpty()) {
            for (Ability ability : abilities) {
                if (ability.isAttack()) {
                    defaultAttack = ability;
                    break;//found an attack, jump out of the loop
                }
            }
        }

        return defaultAttack;
    }

    public void setDefaultAttack(Ability defaultAttack) {
        this.defaultAttack = defaultAttack;
    }

    /**
     * Returns true if the listed item types are in the inventory of this Item.
     *
     * @param items
     * @return
     */
    public boolean hasItems(TreeMap<ItemBlueprint, Integer> items) {
        return items.keySet().stream().noneMatch((blueprint) -> (!hasItems(blueprint, items.get(blueprint))));
    }

    /**
     * Returns true if the listed item type is in the inventory of this object.
     *
     * @param blueprint
     * @param count
     * @return
     */
    public boolean hasItems(ItemBlueprint blueprint, int count) {
        int found = 0;
        for (Item item : inventory) {
            if (item.hasParent(blueprint)) {
                found++;
            }
        }
        return found >= count;
    }

    /**
     * Checks if an item of the given type is equipped.
     *
     * @param blueprint The blueprint to be checked for
     * @return if an item created from the blueprint is found to be equipped
     */
    public boolean hasEquipped(ItemBlueprint blueprint) {
        for (Item item : inventory) {
            if (((item.wearableData != null && item.wearableData.worn)//check worn
                    || (item.wieldableData != null && item.wieldableData.wielded))//check wielded
                    && item.hasParent(blueprint)) {//make sure right item type
                return true;//found!
            }
        }
        return false;//checked everything and didn't find it
    }
}
