package squidpony.epigon.playground;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import squidpony.Maker;
import static squidpony.epigon.Epigon.rng;
import squidpony.epigon.data.blueprint.Inclusion;
import squidpony.squidgrid.gui.gdx.SColor;
import squidpony.squidmath.OrderedMap;

import squidpony.epigon.data.blueprint.PhysicalBlueprint;
import squidpony.epigon.data.blueprint.Stone;
import squidpony.epigon.data.generic.Modification;
import squidpony.epigon.data.generic.Skill;
import squidpony.epigon.data.mixin.Creature;
import squidpony.epigon.data.specific.Condition;
import squidpony.epigon.universe.Element;
import squidpony.epigon.universe.LiveValue;
import squidpony.epigon.universe.Rating;
import squidpony.epigon.universe.Stat;

/**
 * Contains objects to use to test out connections.
 */
public class HandBuilt {

    public List<Stone> wallList = new ArrayList<>(),
        sedimentaryList = new ArrayList<>(),
        intrusiveList = new ArrayList<>(),
        extrusiveList = new ArrayList<>(),
        metamorphicList = new ArrayList<>();

    public List<Inclusion> gemList = new ArrayList<>(),
        sedimentaryGemList = new ArrayList<>(),
        intrusiveGemList = new ArrayList<>(),
        extrusiveGemList = new ArrayList<>(),
        metamorphicGemList = new ArrayList<>();


    public PhysicalBlueprint playerBlueprint;
    public PhysicalBlueprint swordBlueprint;
    public PhysicalBlueprint doorBlueprint;

    public Modification makeWall;
    public Modification makeDoor;
    public Condition openDoor;

    public HandBuilt() {
        initWallLists();

        playerBlueprint = new PhysicalBlueprint();
        playerBlueprint.name = "Plae Haa";
        playerBlueprint.description = "The main player's character.";
        playerBlueprint.notes = "Voted most likely to die in Adventurer's Middle School.";
        playerBlueprint.symbol = '@';
        playerBlueprint.color = SColor.BRIGHT_PINK;
        playerBlueprint.large = true;
        playerBlueprint.unique = true;
        playerBlueprint.possibleAliases = Maker.makeList("Mario", "Link", "Sam");
        Arrays.stream(Stat.values()).forEach(s -> {
            LiveValue lv = new LiveValue(rng.between(20, 100));
            lv.actual = lv.base * (rng.nextDouble() + 0.1);
            playerBlueprint.initialStats.put(s, lv);
        });
        playerBlueprint.initialStats.put(Stat.OPACITY, new LiveValue(100)); // Make sure player's opaque after randomizing stats

        Creature cb = new Creature();
        playerBlueprint.creatureData = cb;
        cb.skills = new OrderedMap<>();
        Skill skill = new Skill();
        skill.name = "kendo";
        cb.skills.put(skill, Rating.HIGH);
        skill = new Skill();
        skill.name = "akido";
        cb.skills.put(skill, Rating.SLIGHT);

        swordBlueprint = new PhysicalBlueprint();
        swordBlueprint.name = "sword";
        swordBlueprint.color = SColor.SILVER;
        swordBlueprint.symbol = '/';

        doorBlueprint = new PhysicalBlueprint();
        doorBlueprint.name = "door";
        doorBlueprint.symbol = '+';
        doorBlueprint.color = SColor.WALNUT;
        doorBlueprint.large = true;
        Arrays.stream(Element.values()).forEach(e -> doorBlueprint.passthroughResistances.put(e, new LiveValue(1)));

        makeWall = new Modification();
        makeWall.possiblePostfix.add("wall");
        makeWall.symbol = '#';
        makeWall.largeOverwrite = true;
        for (Element e : Element.values()) {
            makeWall.passthroughResistancesOverwrite.put(e, new LiveValue(1.0)); // walls block everything
        }

        makeDoor = new Modification();
        makeDoor.possiblePrefix.add("door of");
        makeDoor.symbol = '+';
        makeDoor.largeOverwrite = true;
        for (Element e : Element.values()) {
            makeDoor.passthroughResistancesOverwrite.put(e, new LiveValue(1.0));
        }
        makeDoor.passthroughResistancesOverwrite.put(Element.SONIC, new LiveValue(0.5));
    }


    private void initWallLists() {
        for (Stone stone : Stone.values()) {
            wallList.add(stone);
            if (stone.sedimentary) {
                sedimentaryList.add(stone);
            }
            if (stone.intrusive) {
                intrusiveList.add(stone);
            }
            if (stone.extrusive) {
                extrusiveList.add(stone);
            }
            if (stone.metamorphic) {
                metamorphicList.add(stone);
            }
        }

        for (Inclusion inclusion : Inclusion.values()) {
            gemList.add(inclusion);
            if (inclusion.sedimentary) {
                sedimentaryGemList.add(inclusion);
            }
            if (inclusion.intrusive) {
                intrusiveGemList.add(inclusion);
            }
            if (inclusion.extrusive) {
                extrusiveGemList.add(inclusion);
            }
            if (inclusion.metamorphic) {
                metamorphicGemList.add(inclusion);
            }
        }
    }
}
