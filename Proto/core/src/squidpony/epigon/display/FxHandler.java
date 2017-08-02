package squidpony.epigon.display;

import com.badlogic.gdx.graphics.Color;
import squidpony.ArrayTools;
import squidpony.Maker;
import squidpony.epigon.universe.Element;
import squidpony.squidgrid.Direction;
import squidpony.squidgrid.FOV;
import squidpony.squidgrid.Radius;
import squidpony.squidgrid.gui.gdx.PanelEffect;
import squidpony.squidgrid.gui.gdx.SColor;
import squidpony.squidgrid.gui.gdx.SquidColorCenter;
import squidpony.squidgrid.gui.gdx.SquidPanel;
import squidpony.squidmath.Coord;
import squidpony.squidmath.GreasedRegion;
import squidpony.squidmath.NumberTools;
import squidpony.squidmath.SeededNoise;

import java.util.List;
import java.util.stream.Collectors;

import static squidpony.epigon.Epigon.rng;

/**
 * Controls what happens on the full map overlay panel.
 *
 * @author Eben Howard
 */
public class FxHandler {

    private static char[] explosionChars = new char[]{'%', '$', '&', '!'};

    private SquidPanel fx;
    private int width;
    private int height;
    private SquidColorCenter colorCenter;
    private GreasedRegion viable;
    double[][] seen;

    public FxHandler(SquidPanel panel, SquidColorCenter colorCenter, double[][] visible) {
        fx = panel;
        width = panel.gridWidth();
        height = panel.gridHeight();
        this.colorCenter = colorCenter;
        viable = new GreasedRegion(visible.length, visible[0].length);
        seen = visible;
    }

    public void elementBurst(Coord origin, Element element, int size, Radius radius) {
        fx.addAction(new ConeEffect(fx, 0.85f, viable.refill(seen, 0.001, 999.0),
                origin, size,
                90.0, // the constructor below takes a double before it takes the List<Color>; this was missing
                Maker.makeList(colorCenter.saturate(element.color, 0.3),
                        colorCenter.light(colorCenter.saturate(element.color, 0.15)),
                        colorCenter.lightest(element.color),
                        colorCenter.lighter(colorCenter.desaturate(element.color, 0.15)),
                        colorCenter.desaturate(element.color, 0.3),
                        colorCenter.dim(colorCenter.desaturate(element.color, 0.45)).sub(0, 0, 0, 0.35f),
                        colorCenter.dimmer(colorCenter.desaturate(element.color, 0.6)).sub(0,0,0,0.85f))));
    }

    private void doBurst(int x, int y, Color color) {
        //double tint = 0.5 + rng.nextDouble() / 4;
        char c = explosionChars[rng.nextIntHasty(explosionChars.length)]; // this is pretty much all rng.getRandomElement does
        float timing = rng.nextFloat() * 0.5f;
        fx.summon(timing, x, y, x, y, c, SColor.TRANSPARENT.cpy().sub(0, 0, 0, 1f), color, false, 0, 0, 0.5f);
//        timing += 2f;
//        fx.summon(timing, x, y, x, y, c, color, colorCenter.desaturate(color, tint), false, 0, 0, 0.2f);
    }

    public static class ConeEffect extends PanelEffect
    {
        private final Coord center;
        private final double angle; // This is the full angle of the bilaterally symmetrical shaped triangle coming from the source
        private final int distance;

        /**
         * The default explosion colors are normal for (non-chemical, non-electrical) fire and smoke, going from orange
         * at the start to yellow, very light yellow, and then back to a different orange before going to smoke and
         * fading out to translucent and then transparent by the end.
         * <br>
         * If you want to change the colors the explosion uses, you can either pass a List of Color (or SColor or other
         * subclasses) to the constructor or change this array directly. The float items assigned to this should be the
         * result of calling {@link Color#toFloatBits()} or possibly the result of mixing multiple existing floats with
         * {@link SColor#lerpFloatColors(float, float, float)}; other floats that can be directly used by libGDX, that
         * is, packed as ABGR floats (usually the docs will mention this), can also be used.
         */
        public float[] colors = {
                SColor.INTERNATIONAL_ORANGE.toFloatBits(),
                SColor.FLORAL_LEAF.toFloatBits(),
                SColor.LEMON.toFloatBits(),
                SColor.LEMON_CHIFFON.toFloatBits(),
                SColor.floatGet(0xFF6600EE),  // SColor.SAFETY_ORANGE
                SColor.floatGet(0x595652DD),  // SColor.DB_SOOT
                SColor.floatGet(0x59565299)}; // SColor.DB_SOOT
        /**
         * Used internally to determine how the explosion should spread; derived from {@link #validCells}.
         */
        public double[][] resMap,

        /**
         * The internal representation of how affected each cell is by the explosion, based on proximity to center.
         */
        lightMap;

        /**
         * The raw list of Coords that might be affected by the explosion; may include some cells that aren't going to
         * show as exploding (it usually has some false positives), but shouldn't exclude any cells that should show as
         * such (no false negatives). You can edit this if you need to, but it isn't recommended.
         */
        public List<Coord> affected;

        public ConeEffect(SquidPanel targeting, float duration, GreasedRegion valid, Coord center, int distance, double angle)
        {
            super(targeting, duration, valid);
            this.center = center;
            this.distance = distance;
            this.angle = angle;
            resMap = ArrayTools.fill(1.0, validCells.width, validCells.height);
            validCells.writeDoublesInto(resMap, 0.0);
            lightMap = new double[validCells.width][validCells.height];
            FOV.reuseFOV(resMap, lightMap, center.x, center.y, distance);
            validCells.not().writeDoublesInto(lightMap, 0.0);
            validCells.not();
            affected = Radius.inCircle(center.x, center.y, distance, false, validCells.width, validCells.height)
                .stream()
                .filter(c -> Direction.toGoTo(center, c) == Direction.UP)
                .collect(Collectors.toList());
        }

        public ConeEffect(SquidPanel targeting, float duration, GreasedRegion valid, Coord center, int distance, double angle, List<? extends Color> coloring)
        {
            this(targeting, duration, valid, center, distance, angle);
            if(colors.length != coloring.size())
                colors = new float[coloring.size()];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = coloring.get(i).toFloatBits();
            }
        }
        /**
         * Called each frame.
         *
         * @param percent The percentage of completion for this action, growing from 0 to 1 over the duration. If
         *                {@link #setReverse(boolean) reversed}, this will shrink from 1 to 0.
         */
        @Override
        protected void update(float percent) {
            int len = affected.size();
            Coord c;
            float f, color;
            int idx, seed = System.identityHashCode(this);
            for (int i = 0; i < len; i++) {
                c = affected.get(i);
                if(lightMap[c.x][c.y] <= 0.0)// || 0.6 * (lightMap[c.x][c.y] + percent) < 0.25)
                    continue;
                f = (float)SeededNoise.noise(c.x * 1.5, c.y * 1.5, percent * 5, seed)
                        * 0.17f + percent * 1.2f;
                if(f < 0f || 0.5 * lightMap[c.x][c.y] + f < 0.4)
                    continue;
                idx = (int) (f * colors.length);
                if(idx >= colors.length - 1)
                    color = SColor.lerpFloatColors(colors[colors.length-1], NumberTools.setSelectedByte(colors[colors.length-1], 3, (byte)0), (Math.min(0.99f, f) * colors.length) % 1f);
                else
                    color = SColor.lerpFloatColors(colors[idx], colors[idx+1], (f * colors.length) % 1f);
                target.put(c.x, c.y, color);
            }
        }
    }
}
