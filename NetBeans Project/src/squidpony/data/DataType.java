package squidpony.data;

import squidpony.data.blueprints.ConditionBlueprint;
import squidpony.data.blueprints.BiomeBlueprint;
import squidpony.data.blueprints.BodyBlueprint;
import squidpony.data.blueprints.CreatureBlueprint;
import squidpony.data.blueprints.DungeonBlueprint;
import squidpony.data.blueprints.ItemBlueprint;
import squidpony.data.blueprints.ModificationBlueprint;
import squidpony.data.blueprints.RecipeBlueprint;
import squidpony.data.blueprints.RoomBlueprint;
import squidpony.data.generic.Ability;
import squidpony.data.generic.Element;
import squidpony.data.generic.Profession;
import squidpony.data.generic.Skill;
import squidpony.data.generic.Strategy;
import squidpony.data.generic.TerrainBlueprint;
import squidpony.data.specific.Condition;
import squidpony.data.specific.Biome;
import squidpony.data.specific.Body;
import squidpony.data.specific.Creature;
import squidpony.data.specific.Dungeon;
import squidpony.data.specific.Item;
import squidpony.data.specific.Recipe;
import squidpony.data.specific.Room;

/**
 * Lists the user editable data types.
 */
public enum DataType {

    AURA_BLUEPRINT(ConditionBlueprint.class),
    BIOME_BLUEPRINT(BiomeBlueprint.class),
    BODY_BLUEPRINT(BodyBlueprint.class),
    CREATURE_BLUEPRINT(CreatureBlueprint.class),
    DUNGEON_BLUEPRINT(DungeonBlueprint.class),
    MODIFICATION_BLUEPRINT(ModificationBlueprint.class),
    ITEM_BLUEPRINT(ItemBlueprint.class),
    RECIPE_BLUEPRINT(RecipeBlueprint.class),
    ROOM_BLUEPRINT(RoomBlueprint.class),
    ABILITY(Ability.class),
    ELEMENT(Element.class),
    PROFESSION(Profession.class),
    SKILL(Skill.class),
    STRATEGY(Strategy.class),
    TERRAIN(TerrainBlueprint.class),
    AURA(Condition.class),
    BIOME(Biome.class),
    BODY(Body.class),
    CREATURE(Creature.class),
    DUNGEON(Dungeon.class),
    ITEM(Item.class),
    RECIPE(Recipe.class),
    ROOM(Room.class);
    //
    Class<? extends EpiData> base;

    private DataType(Class<? extends EpiData> base) {
        this.base = base;
    }
}
