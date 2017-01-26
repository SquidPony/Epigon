package squidpony.epigon.data.blueprint;

import squidpony.epigon.data.EpiData;
import squidpony.epigon.data.generic.Modification;
import squidpony.squidmath.OrderedMap;


/**
 * This is used to create specific recipes in a game world.
 *
 * A blueprint is used instead of direct recipes so that more randomness in the recipes is
 * available. Multiple in-game recipes may be created from a single blueprint, each with somewhat
 * different end results based on this blueprint's parameters.
 *
 * The optional consumed and catalyst parts may be present in any combination, with each providing
 * its own changes to the resulting outcome.
 *
 * The difference in which optional things are used and which children of all recipe elements are
 * required is what makes different versions of each recipe possible.
 *
 * The total recipe including results is built at a single time and using special children classes
 * of ingredients does not infer any bonus results not in the built recipe. This may mean that more
 * specific recipes tend to be of higher value but also both harder to find and harder to follow.
 *
 * For example one cake recipe might call for any egg and any flour while another might call for a
 * robin egg and almond flour. Both come from the same base cake recipe but if the first is used
 * then the special properties from a robin egg and almond flour do not get contributed as they
 * would in a more specific recipe.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class RecipeBlueprint extends EpiData {

    public OrderedMap<PhysicalBlueprint, Integer> requiredConsumed;
    public OrderedMap<PhysicalBlueprint, Integer> requiredCatalyst; // ie: a Forge (not consumed)
    public OrderedMap<PhysicalBlueprint, Integer> optionalConsumed; // can add various properties
    public OrderedMap<PhysicalBlueprint, Integer> optionalCatalyst;
    public OrderedMap<PhysicalBlueprint, Integer> result;

    public OrderedMap<PhysicalBlueprint, Modification> modifications; // mapping of what adding optionals does to the results

    public boolean uses(PhysicalBlueprint ingredient) {
        return requiredConsumed.keySet().stream().anyMatch(p -> p.hasParent(ingredient))
            || requiredCatalyst.keySet().stream().anyMatch(p -> p.hasParent(ingredient))
            || optionalConsumed.keySet().stream().anyMatch(p -> p.hasParent(ingredient))
            || optionalCatalyst.keySet().stream().anyMatch(p -> p.hasParent(ingredient));
    }
}
