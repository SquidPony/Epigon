package squidpony.epigon.data.trait;

import squidpony.epigon.data.quality.Inclusion;
import squidpony.epigon.data.quality.Stone;
import squidpony.squidmath.FastNoise;

/**
 * A specific instance of a terrain unit.
 *
 * Should only be created if a generic instance was interacted with in a way that caused it to
 * become different than others of it's type, such as damaged.
 */
public class Terrain {

    public float background;
    public FastNoise noise;

    public Stone stone;
    public Inclusion inclusion;
    public boolean extrusive;
    public boolean intrusive;
    public boolean metamorphic;
    public boolean sedimentary;
}
