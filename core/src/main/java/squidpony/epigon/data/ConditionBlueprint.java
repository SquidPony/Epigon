package squidpony.epigon.data;

import squidpony.epigon.data.quality.Element;
import squidpony.squidmath.OrderedMap;
import squidpony.squidmath.UnorderedSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Blueprint for a Condition.
 *
 * The element associated with a Condition is inherited from the action that
 * applied it.
 *
 * Conditions are temporary (although perhaps indefinite) and can cause any
 * action to be indeterminately applied (such as stat and maximum energy changes
 * and ability availability) or applied on a frequency basis (such as energy
 * damage/healing or movement).
 *
 * When the condition wears off or is canceled there may be some list of actions
 * that take place. Being suppressed does not count as canceled, although if it
 * wears off while being suppressed the effects of wearing off take place.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class ConditionBlueprint extends EpiData {

    public ConditionBlueprint parent;
    public int duration;//how many turns until it wears off -- 0 means it lasts until removed some other way
    public int period;//how often ticks trigger -- 0 means it's constant, 1 means once per round, 2 means every other round, etc.
    public Element baseElement;
    public char overlaySymbol = '\uffff';
    public String verb;
    public ChangeTable changes;
    public List<Modification> tickEffects = new ArrayList<>();
    public List<Modification> wearOffEffects = new ArrayList<>();//what happens when it wears off
    public List<Modification> canceledEffects = new ArrayList<>();//what happens when it's cancelled
    public Set<ConditionBlueprint> conflicts = new UnorderedSet<>();//can't exist at the same time, new one cancels the old one
    public Set<ConditionBlueprint> immunizes = new UnorderedSet<>();//can't exist at the same time, old one prevents the new one from being applies
    public Set<ConditionBlueprint> suppresses = new UnorderedSet<>();//can both exist, but only newest one has effect

    public ConditionBlueprint(String name, String verb, int duration, int period, Element baseElement, char overlay, ChangeTable changes,
                              Collection<Modification> onTick, Collection<Modification> onWearOff, Collection<Modification> onCancel)
    {
        super();
        this.name = name;
        this.verb = verb;
        this.duration = duration;
        this.period = period;
        this.baseElement = baseElement;
        this.overlaySymbol = overlay;
        this.changes = changes;
        if(onTick != null)
            tickEffects.addAll(onTick);
        if(onWearOff != null)
            wearOffEffects.addAll(onWearOff);
        if(onCancel != null)
            canceledEffects.addAll(onCancel);

    }
    public boolean conflictsWith(ConditionBlueprint check) {
        ConditionBlueprint working = check;
        while (working != null) {//go until no more parent found
            if (conflicts.contains(working)) {
                return true;
            }
            working = working.parent;
        }
        return false;//no conflicts found
    }

    public boolean hasParent(ConditionBlueprint blueprint) {
        if (this == blueprint) {
            return true;
        } else if (this.parent == null) {
            return false;
        } else {
            return parent.hasParent(blueprint);
        }
    }

    /*
"Afflict", 
"Bleed", 
"Blind", 
"Chill", 
"Confound", 
"Corrode", 
"Curse", 
"Disable", 
"Disarm", 
"Electrify", 
"Energize",  //helpful
"Favor",  //helpful
"Flare", 
"Ignite", 
"Impale", 
"Irradiate", 
"Punish", 
"Regenerate", //helpful
"Sever", 
"Silence", 
"Slay", 
"Sunder", 
"Trip", 
"Wither", 
     */
    public static OrderedMap<String, ConditionBlueprint> CONDITIONS = OrderedMap.makeMap(
            "Confound", new ConditionBlueprint("confound", "confound$", 3, 0, Element.BLUNT, 'ˀ',
                    ChangeTable.makeCT(CalcStat.PRECISION, (int)'-', 4.0, CalcStat.INFLUENCE, (int)'-', 4.0, CalcStat.CRIT, (int)'-', 2.0), 
                    null, null, null)
            , "Disarm", new ConditionBlueprint("disarm", "disarm$", 1, 0, Element.BLUNT, '\uffff',
                    ChangeTable.makeCT(null, ~'d', 2.0, CalcStat.EVASION, (int)'-', 4.0, CalcStat.DEFENSE, (int)'-', 4.0),
                    null, null, null)
            , "Sunder", new ConditionBlueprint("sunder", "sunder$", 1, 0, Element.BLUNT, '\uffff',
                    ChangeTable.makeCT(null, ~'S', 8.0, Stat.VIGOR, ~'-', 2.0),
                    null, null, null)
            , "Afflict", new ConditionBlueprint("afflict", "afflict$", 6, 1, Element.POISON, '\uffff',
                    ChangeTable.makeCT(Stat.VIGOR, (int)'<', 1.5, CalcStat.PRECISION, (int)'-', 3.0),
                    null, null, null)
            , "Bleed", new ConditionBlueprint("bleed", "cut$", 3, 1, Element.SLASHING, '\uffff',
                    ChangeTable.makeCT(Stat.VIGOR, (int)'<', 3.0, CalcStat.DAMAGE, (int)'-', 1.0),
                    null, null, null)
            , "Chill", new ConditionBlueprint("chill", "chill$", 3, 1, Element.ICE, '▯',
                    ChangeTable.makeCT(Stat.VIGOR, (int)'<', 1.0, CalcStat.QUICKNESS, (int)'-', 3.0, CalcStat.PRECISION, (int)'-', 3.0),
                    null, null, null)
            , "Curse", new ConditionBlueprint("curse", "curse$", 3, 0, Element.FATEFUL, '\uffff',
                    ChangeTable.makeCT(CalcStat.LUCK, (int)'-', 8.0, CalcStat.INFLUENCE, (int)'-', 2.0),
                    null, null, null)
            , "Ignite", new ConditionBlueprint("ignite", "ignite$", 2, 1, Element.FIRE, 'ˇ',
                    ChangeTable.makeCT(Stat.VIGOR, (int)'<', 6.5),
                    null, null, null)
            , "Blind", new ConditionBlueprint("blind", "blind$", 3, 0, Element.SHADOW, '\uffff',
                    ChangeTable.makeCT(Stat.SIGHT, (int)'-', 7.0, CalcStat.PRECISION, (int)'-', 3.0),
                    null, null, null)
            , "Wither", new ConditionBlueprint("wither", "wither$", 15, 2, Element.DEATH, '\uffff',
                    ChangeTable.makeCT(Stat.VIGOR, (int)'<', 1.0),
                    null, null, null)

    );
}
