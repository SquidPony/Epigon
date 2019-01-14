package squidpony.epigon.data.quality;

import com.badlogic.gdx.graphics.Color;
import squidpony.epigon.ConstantKey;
import squidpony.epigon.Utilities;
import squidpony.squidgrid.gui.gdx.SColor;

/**
 * Created by Tommy Ettinger on 12/28/2018.
 */
//¸ grass
//˛ root
//˳ fruit
//ˬ leaf
//˒ thorn
//˷ vine
//∗ flower
//˔ fungus
//∝ cress
public enum Vegetable implements ConstantKey {
    BITTER_YARROW('∗', SColor.COSMIC_LATTE, "a small flower on a long stem"),
    MOSSMELON('˳', SColor.AURORA_FERN_GREEN, "a strange round melon with a mossy rind", "¸≁"),
    SAINT_JOHNʼS_WORT('ˬ', SColor.AURORA_ASPARAGUS, "a squat, round-leafed plant"),
    ANGELCRESS('∝', SColor.AURORA_SAGE_GREEN, "a water plant with lobes that suggest angel wings", "~≁"),
    SNAKEBERRY('˳', SColor.AURORA_EGGPLANT, "a shrub with deep-purple berries that have a scaly texture."),
    GRAY_DOVETHORN('˒', SColor.LAVENDER_GRAY, "a vine that intermingles thorns with gray feathery leaves"),
    RED_RATBANE('˷', SColor.RED_BEAN, "an ugly red vine known to deter rodents", "≁"),
    RASPUTINʼS_SORROW('˛', SColor.AURORA_ZUCCHINI, "a dark vine purported to help men cheat death"),
    DESERT_SAGE('¸', SColor.AURORA_SAGE_GREEN, "a pleasant-smelling dry grass"),
    ALOE_VERA('˒', SColor.AURORA_SILVER_GREEN, "a thorny succulent that hoards moisture in dry deserts"),
    FRAGRANT_CLOVEˉHAZEL('˛', SColor.CLOVE_BROWN, "a rich-brown root with an enticing scent"),
    FLYˉAGARIC_MUSHROOM('˔', SColor.RED_PIGMENT, "a toadstool that is said to bring men to Heaven and Hell"),
    SKULLMALLOW('∝', SColor.CW_ALMOST_WHITE, "a reed that oozes a sticky white sap in the pattern of a skull", "¸≁"),
    NOBLE_LOTUS('∗', SColor.HELIOTROPE, "a beautiful purple flower on a lily pad", "~"),
    BLUE_SWEETLEAF('ˬ', SColor.AURORA_SHARP_AZURE, "a low-lying, bright-blue-leafed shrub that smells like honey"),
    BLOODˉOFˉTHIEVES('˒', SColor.AURORA_FRESH_BLOOD, "a thorny thicket said to prick only those with ill intent"),
    LORDʼS_LILY('∗', SColor.WHITE, "a pure-white flower shaped something like a crown", "~");
    
    private final Color color;
    private final char symbol;
    private final String description;
    private final String prettyName;
    private final String terrains;
    
    Vegetable(char symbol, Color color, String description) {
        this(symbol, color, description, "¸");
    }
    Vegetable(char symbol, Color color, String description, String terrains) {
        this.symbol = symbol;
        this.color = color;
        this.description = description;
        prettyName = Utilities.lower(name(), "_").replace('ˉ', '-');
        this.terrains = terrains;
        hash = ConstantKey.precomputeHash("creature.Vegetable", ordinal());
    }
    public final long hash;
    @Override
    public long hash64() {
        return hash;
    }
    @Override
    public int hash32() {
        return (int)(hash & 0xFFFFFFFFL);
    }

    public String description() {
        return description;
    }

    public String prettyName() {
        return prettyName;
    }

    public Color color() {
        return color;
    }
    public char symbol() {
        return symbol;
    }
    public String terrains()
    {
        return terrains;
    }
    public static final Vegetable[] ALL = values();

}
