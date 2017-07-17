package squidpony.epigon.data.generic;

import squidpony.epigon.data.specific.Physical;
import squidpony.epigon.universe.LiveValue;
import squidpony.epigon.universe.Stat;

/**
 * A way to describe what calculations should be performed at run time.
 *
 * @author Eben Howard
 */
public class Formula {

    public static double baseHitChance(Physical source, Physical target) {
        LiveValue sourceAim = source.stats.get(Stat.AIM);
        if (sourceAim == null || sourceAim.actual() <= 0.0) {
            return 0.0;
        }
        LiveValue targetDodge = target.stats.get(Stat.DODGE);
        if (targetDodge == null || targetDodge.actual() <= 0) {
            return 1.0;
        }

        int[] chances = new int[]{1, 4, 22, 38, 54, 70, 78, 85, 87, 92, 94, 97, 98, 99}; // -5 -> +8
        double difference = sourceAim.actual() - targetDodge.actual();
        difference = Math.max(-5.0, difference);
        difference = Math.min(8.0, difference);

        return chances[(int) Math.round(difference) + 5];
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
