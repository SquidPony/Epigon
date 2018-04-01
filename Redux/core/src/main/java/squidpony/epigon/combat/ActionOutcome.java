package squidpony.epigon.combat;

import squidpony.epigon.ConstantKey;
import squidpony.epigon.GauntRNG;
import squidpony.epigon.data.ConditionBlueprint;
import squidpony.epigon.data.ChangeTable;
import squidpony.epigon.data.Condition;
import squidpony.epigon.data.Physical;
import squidpony.epigon.data.Weapon;
import squidpony.epigon.data.CalcStat;
import squidpony.epigon.data.quality.Element;
import squidpony.epigon.data.LiveValue;
import squidpony.squidmath.Noise;
import squidpony.squidmath.NumberTools;
import squidpony.squidmath.OrderedMap;

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
    public Weapon actorWeapon, targetWeapon;
    public Element element;
    public String actorCondition, targetCondition;
    public ActionOutcome()
    {
    }
    public static final OrderedMap<ConstantKey, LiveValue> tempActorStats = new OrderedMap<>(32, 0.5f, ConstantKey.ConstantKeyHasher.instance),
            tempTargetStats = new OrderedMap<>(32, 0.5f, ConstantKey.ConstantKeyHasher.instance);
    public static void deepCopyInto(OrderedMap<ConstantKey, LiveValue> source, OrderedMap<ConstantKey, LiveValue> toFill)
    {
        final int len = source.size();
        toFill.clear();
        for (int i = 0; i < len; i++) {
            toFill.put(source.keyAt(i), new LiveValue(source.getAt(i)));
        }
    }
    public static ActionOutcome attack(Physical actor, Physical target)
    {
        ActionOutcome ao = new ActionOutcome();
        long r = GauntRNG.nextLong(++actor.chaos);
        ao.actorWeapon = actor.creatureData == null ? Weapon.randomUnarmedWeapon(0L) : actor.creatureData.weaponChoices.random();
        int index = ao.actorWeapon.elements.table.random(r - 1);
        ao.element = ao.actorWeapon.elements.items.get(index);
        ao.targetCondition = index < ao.actorWeapon.statuses.size() ? ao.actorWeapon.statuses.get(index) : GauntRNG.getRandomElement(r - 2, ao.actorWeapon.statuses);
        ao.targetWeapon = target.creatureData == null ? Weapon.randomUnarmedWeapon(0L) : target.creatureData.weaponChoices.random();
        actor.statEffects.add(ao.actorWeapon.calcStats);
        target.statEffects.add(ao.targetWeapon.calcStats);
        ChangeTable.holdPhysical(actor, actor.statEffects);
        //System.out.println("Attacker is " + actor.name + " with base stats " + actor.stats + " and adjusted stats: " + tempActorStats);
        ChangeTable.holdPhysical(target, target.statEffects);
        //System.out.println("Defender is " + target.name  + " with base stats " + target.stats + " and adjusted stats: " + tempTargetStats);
        ao.crit = (20 + 4 * (actor.stats.getOrDefault(CalcStat.CRIT, LiveValue.ZERO).actual() -
                target.stats.getOrDefault(CalcStat.STEALTH, LiveValue.ZERO).actual())) >= GauntRNG.next(r, 9);
        ao.hit = (67 + 5 * ((ao.crit ? 2 : 0) + actor.stats.getOrDefault(CalcStat.PRECISION, LiveValue.ZERO).actual() -
                target.stats.getOrDefault(CalcStat.EVASION, LiveValue.ZERO).actual())) >= GauntRNG.next(r + 1, 7);
        if(ao.hit)
        {
            ao.attemptedDamage = Math.min(0, Noise.fastFloor((NumberTools.randomFloatCurved(r + 2) * 0.4f - 0.45f) * ((ao.crit ? 2 : 1) +
                    actor.stats.getOrDefault(CalcStat.DAMAGE, LiveValue.ZERO).actual())));
            ao.actualDamage = Math.min(0, ao.attemptedDamage -
                    Noise.fastFloor((NumberTools.randomFloatCurved(r + 3) * 0.3f + 0.35f) * target.stats.getOrDefault(CalcStat.DEFENSE, LiveValue.ZERO).actual()));
            ao.targetConditioned = (35 + 5 * ((ao.crit ? 1 : 0) + actor.stats.getOrDefault(CalcStat.INFLUENCE, LiveValue.ZERO).actual() -
                    target.stats.getOrDefault(CalcStat.LUCK, LiveValue.ZERO).actual())) >= GauntRNG.next(r + 4, 8);
        }
        ChangeTable.releasePhysical(actor, actor.statEffects);
        ChangeTable.releasePhysical(target, target.statEffects);
        actor.statEffects.removeLast();
        target.statEffects.removeLast();
        if(ao.targetConditioned)
        {
            Condition c = new Condition(ConditionBlueprint.CONDITIONS.getOrDefault(ao.targetCondition, ConditionBlueprint.CONDITIONS.getAt(0)), target, ao.element);
            ChangeTable.strikePhysical(target, c.parent.changes);
            target.conditions.add(c);
            
        }
        return ao;
    }
}
