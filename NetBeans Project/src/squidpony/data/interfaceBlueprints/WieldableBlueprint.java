package squidpony.data.interfaceBlueprints;

import java.util.ArrayList;
import squidpony.data.generic.Ability;

/**
 *
 * @author Eben
 */
public class WieldableBlueprint {
    public ArrayList<Ability> causes = new ArrayList<>();//conditions imparted by a successful hit
    public int hitChance, damage, distance;
}
