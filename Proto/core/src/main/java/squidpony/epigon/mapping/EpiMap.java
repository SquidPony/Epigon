package squidpony.epigon.mapping;

import squidpony.squidgrid.gui.gdx.SColor;
import squidpony.squidmath.Coord;

import static squidpony.epigon.Epigon.rng;

/**
 * This represents a single explorable map level.
 *
 * Each cell is considered to be 1 meter by 1 meter square.
 *
 * A null tile represents open space with no special properties or opacities to things passing
 * through. They should not be considered a vacuum, but rather normal air.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class EpiMap {

    public int width, height;
    public EpiTile[][] contents;
    public RememberedTile[][] remembered;

    public EpiMap(int width, int height) {
        this.width = width;
        this.height = height;
        contents = new EpiTile[width][height];
        remembered = new RememberedTile[width][height];
    }

    public EpiMap() {
        this(3, 3);
    }

    public boolean inBounds(Coord p) {
        return inBounds(p.x, p.y);
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public double[][] opacities() {
        double[][] resistances = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                resistances[x][y] = contents[x][y].opacity();
            }
        }
        return resistances;
    }

    public char[][] simpleChars() {
        char[][] ret = new char[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                ret[x][y] = contents[x][y].getSymbol();
            }
        }
        return ret;
    }

    public static char altSymbolOf(char symbol) {

        switch (symbol) {
            case '¸'://grass
            case '"':
                return '¸';
            case '~':
                return '≈';
            case 'ø':
                return ' ';
            default://opaque items
                return symbol;
        }
    }

    public static float colorOf(char symbol) {
        float color;
        switch (symbol) {
            case '.'://stone ground
                color = SColor.SLATE_GRAY.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case '"':
            case '¸'://grass
                color = SColor.GREEN.toRandomizedFloat(rng, 0.08f, 0.05f, 0.18f);
                break;
            case ','://pathway
                color = SColor.STOREROOM_BROWN.toRandomizedFloat(rng, 0.04f, 0.05f, 0.1f);
                break;
            case 'c':
                color = SColor.SEPIA.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case '/':
                color = SColor.BROWNER.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case '~':
                color = SColor.AZUL.toRandomizedFloat(rng, 0.1f, 0f, 0.25f);
                break;
            case '≈':
                color = SColor.CW_FLUSH_BLUE.toRandomizedFloat(rng, 0.1f, 0f, 0.2f);
                break;
            case '<':
            case '>':
                color = SColor.SLATE_GRAY.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case 't':
                color = SColor.BROWNER.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case 'm':
                color = SColor.BAIKO_BROWN.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case 'u':
                color = SColor.TAN.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case 'T':
            case '₤':
                color = SColor.FOREST_GREEN.toRandomizedFloat(rng, 0.1f, 0f, 0.2f);
                break;
            case 'E':
                color = SColor.SILVER.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case 'S':
                color = SColor.BREWED_MUSTARD_BROWN.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case '#':
                color = SColor.SLATE_GRAY.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case '+':
                color = SColor.BROWNER.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case 'A':
                color = SColor.ALICE_BLUE.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            case 'ø':
            case ' ':
                color = SColor.DB_INK.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
                break;
            default://opaque items
                color = SColor.DEEP_PINK.toRandomizedFloat(rng, 0.05f, 0f, 0.15f);
        }
        return color;
    }
}
