package squidpony.epigon.combat;

import squidpony.epigon.data.*;
import squidpony.epigon.data.quality.Element;
import squidpony.squidgrid.Radius;
import squidpony.squidmath.Noise;
import squidpony.squidmath.NumberTools;

import java.util.ArrayList;

/**
 * Handles the specific result of one event in a combat, such as whether a sword swing hit, how much damage it did, if
 * a crit occurred (which makes a hit more likely and damage higher, but does not guarantee either), and if conditions
 * were changed, but does not apply to more than one pairing of actor and target. This means an area-of-effect action
 * has multiple ActionOutcomes associated with it, one per target (and a target does not have to be a creature).
 * <br>
 * Created by Tommy Ettinger on 1/10/2018.
 */
public class ActionOutcome {
    public boolean crit, hit, targetConditioned, actorConditioned;
    public int attemptedDamage, actualDamage, actorDamage;
    public Weapon targetWeapon;
    public Weapon actorWeapon;
    public Element element;
    public String actorCondition, targetCondition;
    public ActionOutcome()
    {
    }
    public static ArrayList<ActionOutcome> attack(Physical actor, Physical target)
    {
        ArrayList<ActionOutcome> aos = new ArrayList<>(3);
        if(actor.creatureData != null) {
            int totalWeight = 0;
            for (int i = 0; i < actor.creatureData.weaponChoices.table.size(); i++) {
                ActionOutcome ao = new ActionOutcome();
                Weapon w = actor.creatureData.weaponChoices.table.keyAt(i);
                if(Radius.CIRCLE.radius(actor.location, target.location) > w.rawWeapon.range + 1)
                    continue;
                ao.actorWeapon = w;
                totalWeight += actor.creatureData.weaponChoices.weight(w);
                aos.add(ao);
            }
            for (int wi = 0; wi < aos.size(); wi++) {
                ActionOutcome ao = aos.get(wi);
                Weapon w = ao.actorWeapon;
                int index = w.elements.table.random(actor.nextLong());
                ao.element = w.elements.items.get(index);
                ao.targetCondition = index < w.statuses.size() ? w.statuses.get(index) : actor.getRandomElement(w.statuses);
                ao.targetWeapon = target.creatureData == null ? Weapon.randomUnarmedWeapon(actor) : target.creatureData.weaponChoices.random();
                if(actor.nextInt(totalWeight) >= actor.creatureData.weaponChoices.weight(w))
                    continue;
                int actorSkill = 1, targetSkill = 1;
                if (actor.creatureData != null) {
                    for (int i = 0; i < w.skills.length; i++) {
                        actorSkill += actor.creatureData.skills.getOrDefault(w.skills[i], Rating.NONE).ordinal();
                    }
                }
                if (target.creatureData != null) {
                    for (int i = 0; i < ao.targetWeapon.skills.length; i++) {
                        targetSkill += target.creatureData.skills.getOrDefault(ao.targetWeapon.skills[i], Rating.NONE).ordinal();
                    }
                }
                actor.statEffects.add(w.calcStats);
                target.statEffects.add(ao.targetWeapon.calcStats);
                ChangeTable.holdPhysical(actor, actor.statEffects);
                //System.out.println("Attacker is " + actor.name + " with base stats " + actor.stats + " and adjusted stats: " + tempActorStats);
                ChangeTable.holdPhysical(target, target.statEffects);
                //System.out.println("Defender is " + target.name  + " with base stats " + target.stats + " and adjusted stats: " + tempTargetStats);

                ao.crit = (5 + (actor.actualStat(CalcStat.CRIT) - target.actualStat(CalcStat.STEALTH))) >= actor.nextInt(50);
                double actorPrecision = actor.actualStat(CalcStat.PRECISION) + actorSkill,
                        targetEvasion = target.actualStat(CalcStat.EVASION) + targetSkill;
                ao.hit = (67 + 5 * ((ao.crit ? 2 : 0) + actorPrecision - targetEvasion)) >= actor.next(7);
//        ao.hit = (67 + 5 * ((ao.crit ? 2 : 0) + actor.stats.getOrDefault(CalcStat.PRECISION, LiveValue.ZERO).actual() -
//                target.stats.getOrDefault(CalcStat.EVASION, LiveValue.ZERO).actual())) >= GauntRNG.next(r + 1, 7);
                if (ao.hit) {
                    ao.attemptedDamage = Math.min(0, Noise.fastFloor((NumberTools.formCurvedFloat(actor.nextLong()) * 0.4f - 0.45f) * ((ao.crit ? 2 : 1) +
                            actor.actualStat(CalcStat.DAMAGE) + actorSkill)));
                    ao.actualDamage = Math.min(0, ao.attemptedDamage -
                            Noise.fastFloor((NumberTools.formCurvedFloat(actor.nextLong()) * 0.3f + 0.35f) * (target.actualStat(CalcStat.DEFENSE) + targetSkill)));
                    ao.targetConditioned = (35 + 5 * ((ao.crit ? 1 : 0) + actor.actualStat(CalcStat.INFLUENCE) + actorSkill -
                            target.actualStat(CalcStat.LUCK) - targetSkill)) >= actor.next(8);
                }
                ChangeTable.releasePhysical(actor, actor.statEffects);
                ChangeTable.releasePhysical(target, target.statEffects);
                actor.statEffects.removeLast();
                target.statEffects.removeLast();
                if (ao.targetConditioned) {
                    Condition c = new Condition(ConditionBlueprint.CONDITIONS.getOrDefault(ao.targetCondition, ConditionBlueprint.CONDITIONS.getAt(0)), target, ao.element);
                    ChangeTable.strikePhysical(target, c.parent.changes);
                    target.conditions.add(c);

                }
            }
        }
        return aos;
    }
}
