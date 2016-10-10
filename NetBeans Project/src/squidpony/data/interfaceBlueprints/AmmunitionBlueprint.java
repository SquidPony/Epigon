package squidpony.data.interfaceBlueprints;

import java.util.ArrayList;
import squidpony.data.blueprints.ItemBlueprint;
import squidpony.data.generic.Ability;

/**
 * Holds data for an item that can be used as ammunition.
 *
 * @author Eben Howard - http://squidpony.com - howard@squidpony.com
 */
public class AmmunitionBlueprint {

    public AmmunitionBlueprint parent;
    public ArrayList<Ability> causes = new ArrayList<>();//conditions imparted by a successful hit
    public ArrayList<ItemBlueprint> launchers = new ArrayList<>();//what weapons can use this ammo
    public boolean throwable;
    public int hitChance, damage, distance;
}
