package squidpony.epigon.data.generic;

import static squidpony.epigon.Epigon.rng;
import squidpony.epigon.data.specific.Physical;
import squidpony.epigon.universe.LiveValue;
import squidpony.epigon.universe.Stat;

/**
 * A way to describe what calculations should be performed at run time.
 *
 * @author Eben Howard
 */
public class Formula {

    private static int[] chances = new int[]{1, 4, 22, 38, 54, 70, 78, 85, 87, 92, 94, 97, 98, 99}; // -5 -> +8
    private static int inflection = 5; // how far into the array is 0 stat difference

    public static double opposedRoll(double source, double target) {

        double difference = source - target + inflection;
        if (difference < 0) {
            return chances[0];
        }
        if (difference >= chances.length) {
            return chances[chances.length - 1];
        }

        int under = (int) Math.floor(difference);
        int over = (int) Math.ceil(difference);
        under = chances[under];
        over = chances[over];

        return under + (over - under) * (difference % 1);
    }

    public static double baseHitChance(Physical source, Physical target) {
        LiveValue sourceAim = source.stats.get(Stat.AIM);
        if (sourceAim == null || sourceAim.actual() <= 0.0) {
            return 0.0;
        }
        LiveValue targetDodge = target.stats.get(Stat.DODGE);
        if (targetDodge == null || targetDodge.actual() <= 0) {
            return 1.0;
        }

        return opposedRoll(sourceAim.actual(), targetDodge.actual());
    }

    public static double baseDamageDealt(Physical source, Physical target) {

        LiveValue sourceImpact = source.stats.get(Stat.IMPACT);
        if (sourceImpact == null || sourceImpact.actual() <= 0.0) {
            return 0.0;
        }
        LiveValue targetToughness = target.stats.get(Stat.TOUGHNESS);
        if (targetToughness == null || targetToughness.actual() <= 0) {
            return 1.0;
        }

        double difference = sourceImpact.actual() - targetToughness.actual();
        if (difference < -3) {
            return 1.0;
        }
        if (difference < -2) {
            return 2.0;
        }

        // Xn^1.2 + Xn^(0.2 + 5)
        // Xn starts at 5 for difference of -2
        return Math.pow(difference, 1.2) + Math.pow(difference, 0.2) + 5;
    }

    public static double berserkDamage(Physical source) {
        LiveValue lv = source.stats.get(Stat.IMPACT);
        if (lv == null) {
            return 0.0;
        }
        double val = lv.actual();
        lv = source.stats.get(Stat.LIFE_FORCE);
        if (lv == null) {
            return val;
        }
        if (lv.actual() / lv.base() < 0.2) {
            val *= 3;
        }
        return val;
    }
}
