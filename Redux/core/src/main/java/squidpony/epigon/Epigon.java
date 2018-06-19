package squidpony.epigon;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import squidpony.Messaging;
import squidpony.StringKit;
import squidpony.epigon.combat.ActionOutcome;
import squidpony.epigon.data.*;
import squidpony.epigon.data.quality.Element;
import squidpony.epigon.data.raw.RawCreature;
import squidpony.epigon.data.slot.ClothingSlot;
import squidpony.epigon.data.trait.Grouping;
import squidpony.epigon.data.trait.Interactable;
import squidpony.epigon.display.*;
import squidpony.epigon.display.MapOverlayHandler.PrimaryMode;
import squidpony.epigon.input.ControlMapping;
import squidpony.epigon.input.Verb;
import squidpony.epigon.mapping.*;
import squidpony.epigon.playground.HandBuilt;
import squidpony.panel.IColoredString;
import squidpony.squidai.DijkstraMap;
import squidpony.squidgrid.Direction;
import squidpony.squidgrid.FOV;
import squidpony.squidgrid.Radius;
import squidpony.squidgrid.gui.gdx.*;
import squidpony.squidgrid.gui.gdx.SquidInput.KeyHandler;
import squidpony.squidmath.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static squidpony.epigon.data.Physical.*;
import static squidpony.squidgrid.gui.gdx.SColor.lerpFloatColors;

/**
 * The main class of the game, constructed once in each of the platform-specific Launcher classes.
 * Doesn't use any platform-specific code.
 */
public class Epigon extends Game {
    private enum GameMode {
        DIVE, CRAWL;
        private final String name;

        GameMode() {
            name = Utilities.caps(name(), "_", " ");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Sets a view up to have a map area in the upper left, a info pane to the right, and a message output at the bottom
    public static final PanelSize mapSize;
    public static final PanelSize messageSize;
    public static final PanelSize infoSize;
    public static final PanelSize contextSize;
    public static final int messageCount;
    public static final long seed = 0xBEEFD00DFADEFEEL;
    public final StatefulRNG rng = new StatefulRNG(new LinnormRNG(seed));
    // used for certain calculations where the state changes per-tile
    // allowed to be static because posrng is expected to have its move() method called before each use, which seeds it
    public static final PositionRNG posrng = new PositionRNG(seed ^ seed >>> 1);
    // meant to be used to generate seeds for other RNGs; can be seeded when they should be fixed
    public static final LinnormRNG rootChaos = new LinnormRNG();
    public final RecipeMixer mixer;
    public HandBuilt handBuilt;
    public static final char BOLD = '\u4000', ITALIC = '\u8000', REGULAR = '\0';

    private GameMode mode = GameMode.CRAWL;

    // Audio
    private SoundManager sound;

    // Display
    private SpriteBatch batch;
    private SquidColorCenter colorCenter;
    private SparseLayers mapSLayers;
    private SparseLayers mapOverlaySLayers;
    private SquidLayers infoSLayers;
    private SquidLayers contextSLayers;
    private SquidLayers messageSLayers;
    private SparseLayers fallingSLayers;

    private InputSpecialMultiplexer multiplexer;
    private SquidInput mapInput;
    private SquidInput contextInput;
    private SquidInput infoInput;
    private SquidInput debugInput;
    private SquidInput fallbackInput;
    private Color bgColor, unseenColor;
    private float bgColorFloat, unseenColorFloat, unseenCreatureColorFloat;
    private ArrayList<Coord> toCursor;
    private TextCellFactory font;

    private boolean showingMenu = false;
    private Coord menuLocation = null;
    private Physical currentTarget = null;
    private ArrayList<Weapon> attackOptions = new ArrayList<>(4);

    // Set up the text display portions
    private ArrayList<IColoredString<Color>> messages = new ArrayList<>();
    private int messageIndex;

    private ControlMapping currentMapping;

    // World
    private WorldGenerator worldGenerator;
    private EpiMap[] world;
    private EpiMap map;
    private int depth;
    private FxHandler fxHandler;
    private MapOverlayHandler mapOverlayHandler;
    private ContextHandler contextHandler;
    private InfoHandler infoHandler;
    private FallingHandler fallingHandler;
    private GreasedRegion blocked, floors;
    private DijkstraMap toPlayerDijkstra, monsterDijkstra;
    private Coord cursor;
    private Physical player;
    private ArrayList<Coord> awaitedMoves;
    private OrderedMap<Coord, Physical> creatures;
    private int autoplayTurns = 0;
    private boolean processingCommand = true;

    // Timing
    private long fallDelay = 300;
    private Instant nextFall = Instant.now();
    private boolean paused = true;
    private Instant pausedAt = Instant.now();
    private Instant unpausedAt = Instant.now();
    private long inputDelay = 100;
    private Instant nextInput = Instant.now();
    private long fallDuration = 0L, currentFallDuration = 0L;

    // WIP stuff, needs large sample map
    private Stage mapStage, messageStage, infoStage, contextStage, mapOverlayStage, fallingStage;
    private Viewport mapViewport, messageViewport, infoViewport, contextViewport, mapOverlayViewport, fallingViewport;

    private float[] lightLevels;

    public static final int worldWidth, worldHeight, worldDepth, totalDepth;
    public float startingY, finishY, timeToFall;

    // Set up sizing all in one place
    static {
        worldWidth = 60;
        worldHeight = 60;
        worldDepth = 300;
        totalDepth = worldDepth + World.DIVE_HEADER.length;
        int bigW = World.DIVE_HEADER[0].length() + 2;
        int bigH = 26;
        int smallW = 50;
        int smallH = 22;
        int cellW = 15;
        int cellH = 28;
        int bottomH = 8;
        mapSize = new PanelSize(bigW, bigH, cellW, cellH);
        messageSize = new PanelSize(bigW, bottomH, cellW, cellH);
        infoSize = new PanelSize(smallW, smallH * 7 / 4, 7, 16);
        contextSize = new PanelSize(smallW, (bigH + bottomH - smallH) * 7 / 4, 7, 16);
        messageCount = bottomH - 2;
    }

    public Epigon() {
        mixer = new RecipeMixer();
        handBuilt = new HandBuilt(mixer);
        Weapon.init();
    }

    @Override
    public void create() {
        System.out.println("Working in folder: " + System.getProperty("user.dir"));

        System.out.println("Loading sound manager.");
        sound = new SoundManager();
        colorCenter = new SquidColorCenter();
        colorCenter.granularity = 2;

        System.out.println(rng.getState());

        Coord.expandPoolTo(worldWidth + 1, Math.max(worldHeight, worldDepth + World.DIVE_HEADER.length) + 1);

        bgColor = SColor.WHITE;
        unseenColor = SColor.BLACK_DYE;
        unseenCreatureColorFloat = SColor.CW_DARK_GRAY.toFloatBits();
        bgColorFloat = bgColor.toFloatBits();
        unseenColorFloat = unseenColor.toFloatBits();
        //Some classes in SquidLib need access to a batch to render certain things, so it's a good idea to have one.
        batch = new SpriteBatch();

        System.out.println("Putting together display.");
        mapViewport = new StretchViewport(mapSize.pixelWidth(), mapSize.pixelHeight());
        messageViewport = new StretchViewport(messageSize.pixelWidth(), messageSize.pixelHeight());
        infoViewport = new StretchViewport(infoSize.pixelWidth(), infoSize.pixelHeight());
        contextViewport = new StretchViewport(contextSize.pixelWidth(), contextSize.pixelHeight());
        mapOverlayViewport = new StretchViewport(mapSize.pixelWidth(), mapSize.pixelHeight());
        fallingViewport = new StretchViewport(mapSize.pixelWidth(), mapSize.pixelHeight());

        // Here we make sure our Stages, which holds any text-based grids we make, uses our Batch.
        mapStage = new Stage(mapViewport, batch);
        messageStage = new Stage(messageViewport, batch);
        infoStage = new Stage(infoViewport, batch);
        contextStage = new Stage(contextViewport, batch);
        mapOverlayStage = new Stage(mapOverlayViewport, batch);
        fallingStage = new Stage(fallingViewport, batch);

        font = DefaultResources.getCrispLeanFamily();
        TextCellFactory smallFont = font.copy();
        messageIndex = messageCount;
        messageSLayers = new SquidLayers(
                messageSize.gridWidth,
                messageSize.gridHeight,
                messageSize.cellWidth,
                messageSize.cellHeight,
                font);

        infoSLayers = new SquidLayers(
                infoSize.gridWidth,
                infoSize.gridHeight,
                infoSize.cellWidth,
                infoSize.cellHeight,
                smallFont);
        infoSLayers.getBackgroundLayer().setDefaultForeground(SColor.CW_ALMOST_BLACK);
        infoSLayers.getForegroundLayer().setDefaultForeground(colorCenter.lighter(SColor.CW_PALE_AZURE));

        contextSLayers = new SquidLayers(
                contextSize.gridWidth,
                contextSize.gridHeight,
                contextSize.cellWidth,
                contextSize.cellHeight,
                smallFont);
        contextSLayers.getBackgroundLayer().setDefaultForeground(SColor.CW_ALMOST_BLACK);
        contextSLayers.getForegroundLayer().setDefaultForeground(SColor.CW_PALE_LIME);

        mapSLayers = new SparseLayers(
                worldWidth,
                worldHeight,
                mapSize.cellWidth,
                mapSize.cellHeight,
                font);

        infoHandler = new InfoHandler(infoSLayers, colorCenter);
        contextHandler = new ContextHandler(contextSLayers, mapSLayers);

        mapOverlaySLayers = new SparseLayers(
                mapSize.gridWidth,
                mapSize.gridHeight,
                mapSize.cellWidth,
                mapSize.cellHeight,
                font);
        mapOverlaySLayers.setDefaultBackground(colorCenter.desaturate(SColor.DB_INK, 0.8));
        mapOverlaySLayers.setDefaultForeground(SColor.LIME);
        mapOverlayHandler = new MapOverlayHandler(mapOverlaySLayers);

        fallingSLayers = new SparseLayers(
                100, // weird because falling uses a different view
                totalDepth,
                mapSize.cellWidth,
                mapSize.cellHeight,
                font);
        fallingSLayers.setDefaultBackground(colorCenter.desaturate(SColor.DB_INK, 0.8));
        fallingSLayers.setDefaultForeground(SColor.LIME);
        fallingHandler = new FallingHandler(fallingSLayers);

        font.tweakWidth(mapSize.cellWidth * 1.075f).tweakHeight(mapSize.cellHeight * 1.1f).initBySize();
        smallFont.tweakWidth(infoSize.cellWidth * 1.075f).tweakHeight(infoSize.cellHeight * 1.1f).initBySize();

        // this makes animations very fast, which is good for multi-cell movement but bad for attack animations.
        //mapSLayers.setAnimationDuration(0.145f);

        messageSLayers.setBounds(0, 0, messageSize.pixelWidth(), messageSize.pixelHeight());
        infoSLayers.setBounds(0, 0, infoSize.pixelWidth(), infoSize.pixelHeight());
        contextSLayers.setBounds(0, 0, contextSize.pixelWidth(), contextSize.pixelHeight());
        mapOverlaySLayers.setBounds(0, 0, mapSize.pixelWidth(), mapSize.pixelWidth());
        fallingSLayers.setPosition(0, 0);
        mapSLayers.setPosition(0, 0);

        mapViewport.setScreenBounds(0, messageSize.pixelHeight(), mapSize.pixelWidth(), mapSize.pixelHeight());
        infoViewport.setScreenBounds(mapSize.pixelWidth(), contextSize.pixelHeight(), infoSize.pixelWidth(), infoSize.pixelHeight());
        contextViewport.setScreenBounds(mapSize.pixelWidth(), 0, contextSize.pixelWidth(), contextSize.pixelHeight());
        mapOverlayViewport.setScreenBounds(0, messageSize.pixelHeight(), mapSize.pixelWidth(), mapSize.pixelHeight());
        fallingViewport.setScreenBounds(0, messageSize.pixelHeight(), mapSize.pixelWidth(), mapSize.pixelHeight());

        cursor = Coord.get(-1, -1);

        //This is used to allow clicks or taps to take the player to the desired area.
        toCursor = new ArrayList<>(100);
        awaitedMoves = new ArrayList<>(100);

        mapInput = new SquidInput(mapKeys, mapMouse);
        contextInput = new SquidInput(contextMouse);
        infoInput = new SquidInput(infoMouse);
        debugInput = new SquidInput(debugKeys);
        fallbackInput = new SquidInput(fallbackKeys);
        multiplexer = new InputSpecialMultiplexer(mapInput, contextInput, infoInput, debugInput, fallbackInput); //mapStage, messageStage, 
        Gdx.input.setInputProcessor(multiplexer);

        mapStage.addActor(mapSLayers);
        mapOverlayStage.addActor(mapOverlaySLayers);
        fallingStage.addActor(fallingSLayers);
        messageStage.addActor(messageSLayers);
        infoStage.addActor(infoSLayers);
        contextStage.addActor(contextSLayers);

        fallingStage.getCamera().position.y = startingY = fallingSLayers.worldY(mapSize.gridHeight >> 1);
        finishY = fallingSLayers.worldY(totalDepth);
        timeToFall = Math.abs(finishY - startingY) * fallDelay / mapSize.cellHeight;
        lightLevels = new float[76];
        float initial = lerpFloatColors(RememberedTile.memoryColorFloat, -0x1.7583e6p125F, 0.4f); // the float is SColor.AMUR_CORK_TREE
        for (int i = 0; i < 12; i++) {
            lightLevels[i] = lerpFloatColors(initial, -0x1.7583e6p125F, Interpolation.sineOut.apply(i / 12f)); // AMUR_CORK_TREE again
        }
        for (int i = 0; i < 64; i++) {
            lightLevels[12 + i] = lerpFloatColors(-0x1.7583e6p125F, -0x1.fff1ep126F, Interpolation.sineOut.apply(i / 63f)); // AMUR_CORK_TREE , then ALICE_BLUE
        }

        startGame();
    }

    public static CharSequence style(CharSequence text) {
        return GDXMarkup.instance.styleString(text);
    }

    private void startGame() {
        mapSLayers.clear();
        mapSLayers.glyphs.clear();
        mapSLayers.animationCount = 0;
        handBuilt = new HandBuilt(mixer);

        mapSLayers.addLayer();//first added layer adds at level 1, used for cases when we need "extra background"
        mapSLayers.addLayer();//next adds at level 2, used for the cursor line
        mapSLayers.addLayer();//level 3, backgrounds for hovering menus
        mapSLayers.addLayer();//level 4, text for hovering menus
        mapSLayers.addLayer();//next adds at level 5, used for effects
        IColoredString<Color> emptyICS = IColoredString.Impl.create();
        for (int i = 0; i <= messageCount; i++) {
            messages.add(emptyICS);
        }

        worldGenerator = new WorldGenerator();
        contextHandler.message("Have fun!",
                style("Bump into statues ([*][/]s[,]) and stuff."),
                style("Now [/]90% fancier[/]!"),
                "Use ? for help, or q to quit.",
                "Use numpad or arrow keys to move.");

        initPlayer();

        prepCrawl();
        putCrawlMap();
//        prepFall();

        processingCommand = false; // let the player do input
    }

    private void initPlayer() {
        player = RecipeMixer.buildPhysical(handBuilt.playerBlueprint);
        player.stats.get(Stat.VIGOR).set(99.0);
        player.stats.get(Stat.HUNGER).delta(-0.1);
        player.stats.get(Stat.HUNGER).min(0);
        player.stats.get(Stat.DEVOTION).actual(player.stats.get(Stat.DEVOTION).base() * 1.7);
        player.stats.values().forEach(lv -> lv.max(Double.max(lv.max(), lv.actual())));

        infoHandler.setPlayer(player);
        mapOverlayHandler.setPlayer(player);
        fallingHandler.setPlayer(player);

        infoHandler.showPlayerHealthAndArmor();
    }

    private void prepFall() {
        message("Falling..... Pres SPACE to continue");
        int w = World.DIVE_HEADER[0].length(), d = worldDepth;
        map = worldGenerator.buildDive(w, d, handBuilt);
        contextHandler.setMap(map);

        // Start out in the horizontal middle and visual a bit down
        player.location = Coord.get(w / 2, 0); // for... reasons, y is an offset from the camera position
        fallDuration = 0;
        mode = GameMode.DIVE;
        mapInput.flush();
        mapInput.setRepeatGap(Long.MAX_VALUE);
        mapInput.setKeyHandler(fallingKeys);
        mapInput.setMouse(fallingMouse);
        fallingHandler.show(map);

        paused = true;
        nextFall = Instant.now().plusMillis(fallDelay);
        pausedAt = Instant.now();
    }
    
    private Coord setupLevel(int depth)
    {
        map = world[depth];
        floors.refill(map.opacities(), 0.999);
        GreasedRegion floors2 = floors.copy();
        floors2.andNot(map.downStairPositions).andNot(map.upStairPositions);
        floors2.copy().randomScatter(rng, 3)
                .forEach(c -> map.contents[c.x][c.y].add(RecipeMixer.applyModification(
                        RecipeMixer.buildWeapon(Weapon.randomPhysicalWeapon(player).copy(), player),
                        player.getRandomElement(Element.allEnergy).weaponModification())));
        floors2.randomScatter(rng, 5);
        for (Coord coord : floors2) {
            if (map.contents[coord.x][coord.y].blockage == null) {
                //Physical p = RecipeMixer.buildPhysical(GauntRNG.getRandomElement(rootChaos.nextLong(), Inclusion.values()));
                //RecipeMixer.applyModification(p, handBuilt.makeAlive());
                Physical p = RecipeMixer.buildCreature(RawCreature.ENTRIES[rootChaos.nextInt(RawCreature.ENTRIES.length)]);
                p.color = Utilities.progressiveLighten(p.color);
                Physical pMeat = RecipeMixer.buildPhysical(p);
                RecipeMixer.applyModification(pMeat, handBuilt.makeMeats());
                Physical[] held = new Physical[p.creatureData.equippedDistinct.size()+1];
                p.creatureData.equippedDistinct.toArray(held);
                held[held.length-1]=pMeat;
                double[] weights = new double[held.length];
                Arrays.fill(weights, 1.0);
                weights[held.length-1] = 3.0;
                int[] mins = new int[held.length], maxes = new int[held.length];
                Arrays.fill(mins, 1);
                Arrays.fill(maxes, 1);
                mins[held.length-1] = 2;
                maxes[held.length-1] = 4;
                WeightedTableWrapper<Physical> pt = new WeightedTableWrapper<>(p.nextLong(), held, weights, mins, maxes);
                p.physicalDrops.add(pt);
                p.location = coord;
                map.contents[coord.x][coord.y].add(p);
                map.creatures.put(coord, p);
            }
        }

        return floors2.notAnd(floors).andNot(map.downStairPositions).andNot(map.upStairPositions).singleRandom(rng);
    }
    
    private void prepCrawl() {
        message("Generating crawl.");
        world = worldGenerator.buildWorld(worldWidth, worldHeight, 8, handBuilt);
        depth = 0;
        map = world[depth];
        fxHandler = new FxHandler(mapSLayers, 5, colorCenter, map.fovResult);
        floors = new GreasedRegion(map.width, map.height);
        for (int i = world.length - 1; i > 0; i--) {
            setupLevel(i);
        }
        player.location = setupLevel(0);
        map.contents[player.location.x][player.location.y].add(player);
        char[][] simple = new char[map.width][map.height];
        RNG dijkstraRNG = new RNG();// random seed, player won't make deterministic choices
        toPlayerDijkstra = new DijkstraMap(simple, DijkstraMap.Measurement.EUCLIDEAN, dijkstraRNG);
        monsterDijkstra = new DijkstraMap(simple, DijkstraMap.Measurement.EUCLIDEAN, dijkstraRNG); // shared RNG

        blocked = new GreasedRegion(map.width, map.height);
        changeLevel(0);

        if (player.appearance != null) {
            mapSLayers.removeGlyph(player.appearance);
        }
        player.appearance = mapSLayers.glyph(player.symbol, player.color, player.location.x, player.location.y);

        mode = GameMode.CRAWL;
        mapInput.flush();
        mapInput.setRepeatGap(220);
        mapInput.setKeyHandler(mapKeys);
        mapInput.setMouse(mapMouse);
    }
    
    private void changeLevel(int level)
    {
        depth = level;
        map = world[depth];
        mapSLayers.clear();
        for (int i = mapSLayers.glyphs.size() - 1; i >= 0; i--) {
            mapSLayers.removeGlyph(mapSLayers.glyphs.get(i));
        }
        player.appearance = mapSLayers.glyph(player.symbol, player.color, player.location.x, player.location.y);
        contextHandler.setMap(map);
        fxHandler.seen = map.fovResult;
        creatures = map.creatures;
        calcFOV(player.location.x, player.location.y);
        char[][] simple = map.simpleChars();
        toPlayerDijkstra.initialize(simple);
        monsterDijkstra.initialize(simple);
        calcDijkstra();
    }

    private void runTurn() {
        int size = creatures.size();
        Set<Coord> creaturePositions = creatures.keySet();
        for (int i = 0; i < size; i++) {
            final Physical creature = creatures.getAt(i);
            creature.update();
            if (creature.overlaySymbol == '\uffff') {
                mapSLayers.removeGlyph(creature.overlayAppearance);
                creature.overlayAppearance = null;
            }
            Coord c = creature.location;
            if (creature.stats.get(Stat.MOBILITY).actual() > 0 && (map.fovResult[c.x][c.y] > 0)) {
                List<Coord> path = monsterDijkstra.findPath(1, 5, creaturePositions, null, c, player.location);
                if (path != null && !path.isEmpty()) {
                    Coord step = path.get(0);
                    if (player.location.x == step.x && player.location.y == step.y) {
                        ArrayList<ActionOutcome> aos = ActionOutcome.attack(creature, player);
                        for(ActionOutcome ao : aos) {
                            Element element = ao.element;
                            fxHandler.attackEffect(creature, player, ao);
                            if (ao.hit) {
                                int amt = ao.actualDamage >> 1;
                                applyStatChange(player, Stat.VIGOR, amt);
                                amt *= -1; // flip sign for output message
                                if (player.stats.get(Stat.VIGOR).actual() <= 0) {
                                    if (ao.crit) {
                                        message(Messaging.transform("The " + creature.name + " [Blood]brutally[] slay$ you with "
                                                + amt + " " + element.styledName + " damage!", player.name, Messaging.NounTrait.NO_GENDER));
                                    } else {
                                        message(Messaging.transform("The " + creature.name + " slay$ you with "
                                                + amt + " " + element.styledName + " damage!", player.name, Messaging.NounTrait.NO_GENDER));
                                    }
                                } else {
                                    if (ao.crit) {
                                        mapSLayers.wiggle(player.appearance, 0.4f);
                                        message(Messaging.transform("The " + creature.name + " [CW Bright Orange]critically[] " + element.verb + " you for "
                                                + amt + " " + element.styledName + " damage!", player.name, Messaging.NounTrait.NO_GENDER));
                                    } else {
                                        message(Messaging.transform("The " + creature.name + " " + element.verb + " you for "
                                                + amt + " " + element.styledName + " damage!", creature.name, Messaging.NounTrait.NO_GENDER));
                                    }
                                    if (ao.targetConditioned) {
                                        message(Messaging.transform("The " + creature.name + " "
                                                + ConditionBlueprint.CONDITIONS.getOrDefault(ao.targetCondition, ConditionBlueprint.CONDITIONS.getAt(0)).verb + " you with @my attack!", creature.name, Messaging.NounTrait.NO_GENDER));
                                        if (player.overlaySymbol != '\uffff') {
                                            if (player.overlayAppearance != null) {
                                                mapSLayers.removeGlyph(player.overlayAppearance);
                                            }
                                            player.overlayAppearance = mapSLayers.glyph(player.overlaySymbol, player.overlayColor, step.x, step.y);
                                        }
                                    }
                                }
                            } else {
                                if (ao.crit) {
                                    message("The " + creature.name + " missed you, but just barely.");
                                } else {
                                    message("The " + creature.name + " missed you.");
                                }
                            }
                        }
                    } else if (map.contents[step.x][step.y].blockage == null && !creatures.containsKey(step)) {
                        map.contents[c.x][c.y].remove(creature);
                        if (creature.appearance == null) {
                            creature.appearance = mapSLayers.glyph(creature.symbol, creature.color, c.x, c.y);
                            if (creature.overlaySymbol != '\uffff')
                                creature.overlayAppearance = mapSLayers.glyph(creature.overlaySymbol, creature.overlayColor, c.x, c.y);
                        }
                        creatures.alterAt(i, step);
                        creature.location = step;
                        map.contents[step.x][step.y].add(creature);
                        mapSLayers.slide(creature.appearance, c.x, c.y, step.x, step.y, 0.145f, null);
                        if (creature.overlayAppearance != null)
                            mapSLayers.slide(creature.overlayAppearance, c.x, c.y, step.x, step.y, 0.145f, null);
                    }
                }
            }
        }

        // Update all the stats in motion
        OrderedMap<ConstantKey, Double> changes = new OrderedMap<>(ConstantKey.ConstantKeyHasher.instance);
        for (Entry<ConstantKey, LiveValue> entry : player.stats.entrySet()) {
            double amt = entry.getValue().tick();
            if (amt != 0) {
                changes.put(entry.getKey(), amt);
            }
        }
        for (Stat s : Stat.rolloverProcessOrder) {
            double val = player.stats.get(s).actual();
            if (val < 0) {
                player.stats.get(s).actual(0);
                player.stats.get(s.getRollover()).actual(player.stats.get(s.getRollover()).actual() + val);
                changes.merge(s.getRollover(), val, Double::sum);
            }
        }

        infoHandler.updateDisplay(player, changes);
        if (player.stats.get(Stat.VIGOR).actual() <= 0) {
            message("You are now dead with Vigor: " + player.stats.get(Stat.VIGOR).actual());
        }

        if (autoplayTurns > 0) {
            autoplayTurns--;
            Timer.schedule(new Task() {
                @Override
                public void run() {
                    move(rng.getRandomElement(Arrays.stream(Direction.OUTWARDS)
                            .filter(d -> map.contents[player.location.x + d.deltaX][player.location.y + d.deltaY].getLargeNonCreature() == null)
                            .collect(Collectors.toList())
                    ));
                }
            }, 0.2f);
        }
    }

    private void applyStatChange(Physical target, Map<ConstantKey, Double> amounts) {
        OrderedMap<ConstantKey, Double> changes = new OrderedMap<>(ConstantKey.ConstantKeyHasher.instance);
        for (Entry<ConstantKey, LiveValue> entry : target.stats.entrySet()) {
            Double amount = amounts.get(entry.getKey());
            if (amount != null) {
                changes.put(entry.getKey(), amount);
                entry.getValue().addActual(amount);
            }
        }
        for (Stat s : Stat.rolloverProcessOrder) {
            LiveValue lv = target.stats.get(s);
            if (lv == null) {
                continue; // doesn't have this stat so skip it
            }
            double val = lv.actual();
            if (val < 0) {
                target.stats.get(s).actual(0);
                target.stats.get(s.getRollover()).addActual(val);
                changes.merge(s.getRollover(), val, Double::sum);
            }
        }

        infoHandler.updateDisplay(target, changes);
    }

    private void applyStatChange(Physical target, Stat stat, double amount) {
        OrderedMap<ConstantKey, Double> changes = new OrderedMap<>(ConstantKey.ConstantKeyHasher.instance);
        changes.put(stat, amount);
        target.stats.get(stat).addActual(amount);
        for (Stat s : Stat.rolloverProcessOrder) {
            LiveValue lv = target.stats.get(s);
            if (lv == null) {
                continue; // doesn't have this stat so skip it
            }
            double val = lv.actual();
            if (val < 0) {
                target.stats.get(s).actual(0);
                target.stats.get(s.getRollover()).addActual(val);
                changes.merge(s.getRollover(), val, Double::sum);
            }
        }

        infoHandler.updateDisplay(target, changes);
    }

    private void clearContents(SquidLayers layers, Color background) {
        int w = layers.getGridWidth();
        int h = layers.getGridHeight();
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < h - 1; y++) {
                layers.put(x, y, ' ', SColor.TRANSPARENT, background);
            }
        }
    }

    private void clearAndBorder(SquidLayers layers, Color borderColor, Color background) {
        clearContents(layers, background);

        int w = layers.getGridWidth();
        int h = layers.getGridHeight();
        for (int x = 0; x < w; x++) {
            layers.put(x, 0, '─', borderColor, background);
            layers.put(x, h - 1, '─', borderColor, background);
        }
        for (int y = 0; y < h; y++) {
            layers.put(0, y, '│', borderColor, background);
            layers.put(w - 1, y, '│', borderColor, background);
        }
        layers.put(0, 0, '┌', borderColor, background);
        layers.put(w - 1, 0, '┐', borderColor, background);
        layers.put(0, h - 1, '└', borderColor, background);
        layers.put(w - 1, h - 1, '┘', borderColor, background);
    }

    public void updateMessages() {
        clearAndBorder(messageSLayers, SColor.APRICOT, unseenColor);
        for (int i = messageIndex, c = 0; i >= 0 && c < messageCount; i--, c++) {
            messageSLayers.getForegroundLayer().put(1, messageCount - c, messages.get(i));
        }
    }

    /**
     * @param amount negative to scroll to previous messages, positive for later messages
     */
    private void scrollMessages(int amount) {
        messageIndex = MathExtras.clamp(messageIndex + amount, messageCount, messages.size() - 1);
        updateMessages();
    }

    private void message(String text) {
        messageIndex = Math.max(messages.size(), messageCount);
        messages.add(GDXMarkup.instance.colorString("[]" + text));
        updateMessages();
        /*
        messages.set(messageIndex++ % messageCount, GDXMarkup.instance.colorString("[]"+text));
        for (int i = messageIndex % messageCount, c = 0; c < messageCount; i = (i + 1) % messageCount, c++) {
            messageSLayers.getForegroundLayer().put(1, 1 + c, messages.get(i));
        }
         */
    }

    private void calcFOV(int checkX, int checkY) {
        FOV.reuseLOS(map.opacities(), map.losResult, checkX, checkY);
        FOV.reuseFOV(map.resistances, map.fovResult, checkX, checkY, player.stats.get(Stat.SIGHT).actual(), Radius.CIRCLE);
        SColor.eraseColoredLighting(map.colorLighting);
        Radiance radiance;
        for (int x = 0; x < map.width; x++) {
            for (int y = 0; y < map.height; y++) {
                if((radiance = map.contents[x][y].getAnyRadiance()) != null)
                {
                    FOV.reuseFOV(map.resistances, map.tempFOV, x, y, radiance.range);
                    SColor.colorLightingInto(map.tempColorLighting, map.tempFOV, radiance.color);
                    SColor.mixColoredLighting(map.colorLighting, map.tempColorLighting);
                }
            }
        }
        for (int x = 0; x < map.width; x++) {
            for (int y = 0; y < map.height; y++) {
                if(map.losResult[x][y] > 0.0)
                {
                    map.fovResult[x][y] = MathUtils.clamp(map.fovResult[x][y] + map.colorLighting[0][x][y], 0.0, 1.0);
                }
            }
        }
        

        Physical creature;
        for (int x = 0; x < map.width; x++) {
            for (int y = 0; y < map.height; y++) {
                if (map.fovResult[x][y] > 0) {
                    posrng.move(x, y);
                    if (map.remembered[x][y] == null) {
                        map.remembered[x][y] = new RememberedTile(map.contents[x][y]);
                    } else {
                        map.remembered[x][y].remake(map.contents[x][y]);
                    }
                    if ((creature = creatures.get(Coord.get(x, y))) != null) {
                        if (creature.appearance == null)
                            creature.appearance = mapSLayers.glyph(creature.symbol, creature.color, x, y);
                        else if (!mapSLayers.glyphs.contains(creature.appearance)) {
                            mapSLayers.glyphs.add(creature.appearance);
                            if (creature.overlayAppearance != null)
                                mapSLayers.glyphs.add(creature.overlayAppearance);
                        }
                    }
                } else if ((creature = creatures.get(Coord.get(x, y))) != null && creature.appearance != null) {
                    mapSLayers.removeGlyph(creature.appearance);
                    if (creature.overlayAppearance != null)
                        mapSLayers.removeGlyph(creature.overlayAppearance);
                }
            }
        }
    }

    private void calcDijkstra() {
        toPlayerDijkstra.clearGoals();
        toPlayerDijkstra.resetMap();
        monsterDijkstra.clearGoals();
        monsterDijkstra.resetMap();
        toPlayerDijkstra.setGoal(player.location);
        toPlayerDijkstra.scan(blocked);
    }

    private void equipItem() {
        if (player.inventory.isEmpty()) {
            message("Nothing equippable found.");
        } else {
            player.shuffleInPlace(player.inventory);
            for (int i = 0; i < player.inventory.size(); i++) {
                Physical chosen = player.inventory.get(i);
                if (chosen.weaponData != null) {
                    equipItem(chosen);
                    return;
                }
            }
//            if(player.statEffects.contains(player.weaponData.calcStats))
//                player.statEffects.alter(player.weaponData.calcStats, (player.weaponData = player.unarmedData).calcStats);
//            else
//                player.statEffects.add((player.weaponData = player.unarmedData).calcStats);

        }
    }
    
    private void equipItem(Physical item) {
//        if(player.statEffects.contains(player.weaponData.calcStats))
//            player.statEffects.alter(player.weaponData.calcStats, (player.weaponData = item.weaponData).calcStats);
//        else
//            player.statEffects.add((player.weaponData = item.weaponData).calcStats);
        switch (item.weaponData.hands) {
            case 2:
                player.equip(item, BOTH);
                break;
            case 0:
                player.creatureData.weaponChoices.add(item.weaponData, 1);
                break;
            case 3:
                if (!player.creatureData.equippedBySlot.containsKey(ClothingSlot.HEAD))
                    player.equip(item, HEAD);
                break;
            case 4:
                if (!player.creatureData.equippedBySlot.containsKey(ClothingSlot.NECK))
                    player.equip(item, NECK);
                break;
            case 5:
                if (!player.creatureData.equippedBySlot.containsKey(ClothingSlot.LEFT_FOOT) && !player.creatureData.equippedBySlot.containsKey(ClothingSlot.RIGHT_FOOT))
                    player.equip(item, FEET);
                break;
            case 1:
                if (!player.creatureData.equippedBySlot.containsKey(ClothingSlot.RIGHT_HAND))
                    player.equip(item, RIGHT);
                else
                    player.equip(item, LEFT);
                break;
        }
    }

    private void scheduleMove(Direction dir) {
        awaitedMoves.add(player.location.translate(dir));
    }

    private void validAttackOptions(Physical target) {
        Arrangement<Weapon> table = player.creatureData.weaponChoices.table;
        Weapon w;
        currentTarget = target;
        attackOptions.clear();
        for (int i = 0; i < table.size(); i++) {
            w = table.keyAt(i);
            if(Radius.CIRCLE.radius(player.location, target.location) <= w.rawWeapon.range + 1.5) 
                attackOptions.add(w);
        }
    }
    private Coord showAttackOptions(Physical target, ArrayList<Weapon> options) {
        // = validAttackOptions(target);
        int sz = options.size(), len = 0;
        for (int i = 0; i < sz; i++) {
            len = Math.max(options.get(i).rawWeapon.name.length(), len);
        }
        int startY = MathUtils.clamp(target.location.y - (sz >> 1), 0, map.height - sz - 1),
                startX = target.location.x+1;
        final float smoke = SColor.translucentColor( -0x1.fefefep125F, 0.777f); //SColor.CW_GRAY
        if(target.location.x+len+1 < map.width) {
            for (int i = 0; i < sz; i++) {
                String name = options.get(i).rawWeapon.name;
                for (int j = 0; j < len; j++) {
                    mapSLayers.put(startX + j, startY + i, '\u0000',
                            smoke,
                            0f, 3);
                }
                mapSLayers.put(startX, startY+i, name, SColor.COLOR_WHEEL_PALETTE_BRIGHT[(i*3)&15], null, 4);
            }
        }
        else {
            startX = target.location.x - len;
            for (int i = 0; i < sz; i++) {
                String name = options.get(i).rawWeapon.name;
                for (int j = 0; j < len; j++) {
                    mapSLayers.put(startX + j, startY + i, '\u0000',
                            smoke,
                            0f, 3);
                }
                mapSLayers.put(target.location.x-name.length(), startY+i, name, SColor.COLOR_WHEEL_PALETTE_BRIGHT[(i*3)&15], null, 4);
            }
        }
        showingMenu = true;
        return Coord.get(startX, startY);
    }

    private void attack(Physical target)
    {
        int targetX = target.location.x, targetY = target.location.y;
        ArrayList<ActionOutcome> aos = ActionOutcome.attack(player, target);
        for(ActionOutcome ao : aos) {
            Element element = ao.element;
            fxHandler.attackEffect(player, target, ao);
            //mapSLayers.bump(player.appearance, dir, 0.145f);
            if (ao.hit) {
                applyStatChange(target, Stat.VIGOR, ao.actualDamage);
                if (target.stats.get(Stat.VIGOR).actual() <= 0) {
                    mapSLayers.removeGlyph(target.appearance);
                    if (target.overlayAppearance != null) {
                        mapSLayers.removeGlyph(target.overlayAppearance);
                    }
                    creatures.remove(target.location);
                    map.contents[targetX][targetY].remove(target);
                    if (ao.crit) {
                        Stream.concat(target.physicalDrops.stream(), target.elementDrops.getOrDefault(element, Collections.emptyList()).stream())
                                .map(table -> {
                                    int quantity = table.quantity();
                                    Physical p = RecipeMixer.buildPhysical(table.random());
                                    if (p.groupingData != null) {
                                        p.groupingData.quantity += quantity;
                                    } else {
                                        p.groupingData = new Grouping(quantity);
                                    }
                                    return p;
                                })
                                .forEach(item -> {
                                    map.contents[targetX][targetY].add(item);
                                    if (map.resistances[targetX + player.between(-1, 2)][targetY + player.between(-1, 2)] < 0.9) {
                                        map.contents[targetX + player.between(-1, 2)][targetY + player.between(-1, 2)].add(item);
                                    }
                                });
                        mapSLayers.burst(targetX, targetY, 1, Radius.CIRCLE, target.appearance.shown, target.color, SColor.translucentColor(target.color, 0f), 1);
                        message("You [Blood]brutally[] defeat the " + target.name + " with " + -ao.actualDamage + " " + element.styledName + " damage!");
                    } else {
                        Stream.concat(target.physicalDrops.stream(), target.elementDrops.getOrDefault(element, Collections.emptyList()).stream())
                                .map(table -> {
                                    int quantity = table.quantity();
                                    Physical p = RecipeMixer.buildPhysical(table.random());
                                    if (p.groupingData != null) {
                                        p.groupingData.quantity += quantity;
                                    } else {
                                        p.groupingData = new Grouping(quantity);
                                    }
                                    return p;
                                })
                                .forEach(item -> map.contents[targetX][targetY].add(item));
                        mapSLayers.burst(targetX, targetY, 1, Radius.CIRCLE, target.appearance.shown, target.color, SColor.translucentColor(target.color, 0f), 1);
                        message("You defeat the " + target.name + " with " + -ao.actualDamage + " " + element.styledName + " damage!");
                    }
                } else {
                    String amtText = String.valueOf(-ao.actualDamage);
                    if (ao.crit) {
                        mapSLayers.wiggle(0.0f, target.appearance, 0.4f, () -> target.appearance.setPosition(
                                mapSLayers.worldX(target.location.x), mapSLayers.worldY(target.location.y)));
                        message(Messaging.transform("You [CW Bright Orange]critically[] " + element.verb + " the " + target.name + " for " +
                                amtText + " " + element.styledName + " damage!", "you", Messaging.NounTrait.SECOND_PERSON_SINGULAR));
                    } else {
                        message(Messaging.transform("You " + element.verb + " the " + target.name + " for " +
                                amtText + " " + element.styledName + " damage!", "you", Messaging.NounTrait.SECOND_PERSON_SINGULAR));
                    }
                    if (ao.targetConditioned) {
                        message(Messaging.transform("You " +
                                ConditionBlueprint.CONDITIONS.getOrDefault(ao.targetCondition, ConditionBlueprint.CONDITIONS.getAt(0)).verb +
                                " the " + target.name + " with your attack!", "you", Messaging.NounTrait.SECOND_PERSON_SINGULAR));
                        if (target.overlaySymbol != '\uffff') {
                            if (target.overlayAppearance != null) mapSLayers.removeGlyph(target.overlayAppearance);
                            target.overlayAppearance = mapSLayers.glyph(target.overlaySymbol, target.overlayColor, targetX, targetY);
                        }
                    }
                }
            } else {
                message("Missed the " + target.name + (ao.crit ? ", but just barely." : "..."));
            }
        }
    }
    private void attack(Physical target, Weapon choice) {
        int targetX = target.location.x, targetY = target.location.y;
        ActionOutcome ao = ActionOutcome.attack(player, choice, target);
        Element element = ao.element;
        fxHandler.attackEffect(player, target, ao);
        //mapSLayers.bump(player.appearance, dir, 0.145f);
        if (ao.hit) {
            applyStatChange(target, Stat.VIGOR, ao.actualDamage);
            if (target.stats.get(Stat.VIGOR).actual() <= 0) {
                mapSLayers.removeGlyph(target.appearance);
                if (target.overlayAppearance != null) {
                    mapSLayers.removeGlyph(target.overlayAppearance);
                }
                creatures.remove(target.location);
                map.contents[targetX][targetY].remove(target);
                if (ao.crit) {
                    Stream.concat(target.physicalDrops.stream(), target.elementDrops.getOrDefault(element, Collections.emptyList()).stream())
                            .map(table -> {
                                int quantity = table.quantity();
                                Physical p = RecipeMixer.buildPhysical(table.random());
                                if (p.groupingData != null) {
                                    p.groupingData.quantity += quantity;
                                } else {
                                    p.groupingData = new Grouping(quantity);
                                }
                                return p;
                            })
                            .forEach(item -> {
                                map.contents[targetX][targetY].add(item);
                                if (map.resistances[targetX + player.between(-1, 2)][targetY + player.between(-1, 2)] < 0.9) {
                                    map.contents[targetX + player.between(-1, 2)][targetY + player.between(-1, 2)].add(item);
                                }
                            });
                    mapSLayers.burst(targetX, targetY, 1, Radius.CIRCLE, target.appearance.shown, target.color, SColor.translucentColor(target.color, 0f), 1);
                    message("You [Blood]brutally[] defeat the " + target.name + " with " + -ao.actualDamage + " " + element.styledName + " damage!");
                } else {
                    Stream.concat(target.physicalDrops.stream(), target.elementDrops.getOrDefault(element, Collections.emptyList()).stream())
                            .map(table -> {
                                int quantity = table.quantity();
                                Physical p = RecipeMixer.buildPhysical(table.random());
                                if (p.groupingData != null) {
                                    p.groupingData.quantity += quantity;
                                } else {
                                    p.groupingData = new Grouping(quantity);
                                }
                                return p;
                            })
                            .forEach(item -> map.contents[targetX][targetY].add(item));
                    mapSLayers.burst(targetX, targetY, 1, Radius.CIRCLE, target.appearance.shown, target.color, SColor.translucentColor(target.color, 0f), 1);
                    message("You defeat the " + target.name + " with " + -ao.actualDamage + " " + element.styledName + " damage!");
                }
            } else {
                String amtText = String.valueOf(-ao.actualDamage);
                if (ao.crit) {
                    mapSLayers.wiggle(0.0f, target.appearance, 0.4f, () -> target.appearance.setPosition(
                            mapSLayers.worldX(target.location.x), mapSLayers.worldY(target.location.y)));
                    message(Messaging.transform("You [CW Bright Orange]critically[] " + element.verb + " the " + target.name + " for " +
                            amtText + " " + element.styledName + " damage!", "you", Messaging.NounTrait.SECOND_PERSON_SINGULAR));
                } else {
                    message(Messaging.transform("You " + element.verb + " the " + target.name + " for " +
                            amtText + " " + element.styledName + " damage!", "you", Messaging.NounTrait.SECOND_PERSON_SINGULAR));
                }
                if (ao.targetConditioned) {
                    message(Messaging.transform("You " +
                            ConditionBlueprint.CONDITIONS.getOrDefault(ao.targetCondition, ConditionBlueprint.CONDITIONS.getAt(0)).verb +
                            " the " + target.name + " with your attack!", "you", Messaging.NounTrait.SECOND_PERSON_SINGULAR));
                    if (target.overlaySymbol != '\uffff') {
                        if (target.overlayAppearance != null) mapSLayers.removeGlyph(target.overlayAppearance);
                        target.overlayAppearance = mapSLayers.glyph(target.overlaySymbol, target.overlayColor, targetX, targetY);
                    }
                }
            }
        } else {
            message("Missed the " + target.name + (ao.crit ? ", but just barely." : "..."));
        }
    }
    /**
     * Move the player if he isn't bumping into a wall or trying to go off the map somehow.
     */
    private void move(Direction dir) {
        player.update();
        if (player.overlaySymbol == '\uffff') {
            mapSLayers.removeGlyph(player.overlayAppearance);
            player.overlayAppearance = null;
        }
        int newX = player.location.x + dir.deltaX;
        int newY = player.location.y + dir.deltaY;
        Coord newPos = Coord.get(newX, newY);
        if (!map.inBounds(newX, newY)) {
            return; // can't move, should probably be error or something
        }
        map.contents[player.location.x][player.location.y].remove(player);
        if (map.contents[newX][newY].blockage == null) {
            mapSLayers.slide(player.appearance, player.location.x, player.location.y, newX, newY, 0.145f, () ->
            {
                calcFOV(newX, newY);
                calcDijkstra();
                runTurn();
            });
            if (player.overlayAppearance != null)
                mapSLayers.slide(player.overlayAppearance, player.location.x, player.location.y, newX, newY, 0.145f, null);
            player.location = newPos;
            map.contents[player.location.x][player.location.y].add(player);
            sound.playFootstep();
        } else {
            Physical thing = map.contents[newX][newY].getCreature();
            if (thing != null) {
                awaitedMoves.clear(); // don't keep moving if something hit
                toCursor.clear();
                attack(thing);
                calcFOV(player.location.x, player.location.y);
                calcDijkstra();
                runTurn();
            } else if ((thing = map.contents[newX][newY].getLargeNonCreature()) != null) {
                awaitedMoves.clear(); // don't keep moving if something hit
                toCursor.clear();
                message("Ran into " + thing.name);
                runTurn();
            } else {
                runTurn();
            }
        }
    }

    public void putWithLight(int x, int y, char c, float foreground) {
        // The NumberTools.swayTight call here helps increase the randomness in a way that isn't directly linked to the other parameters.
        // By multiplying noise by pi here, it removes most of the connection between swayTight's result and the other calculations involving noise.
//        lightAmount = Math.max(0, Math.min(lightAmount - NumberTools.swayTight(noise * 3.141592f) * 0.1f - 0.1f + 0.2f * noise, lightAmount)); // 0.1f * noise for light theme, 0.2f * noise for dark theme
//        int n = (int) (lightAmount * lightLevels.length);
//        n = Math.min(Math.max(n, 0), lightLevels.length - 1);


        //float back = lightLevels[n]; // background gets both lit and faded to memory
        //mapSLayers.put(x, y, c, front, back); // "light" theme
        //mapSLayers.put(x, y, c, lerpFloatColors(foreground, lightLevels[n], 0.5f), RememberedTile.memoryColorFloat); // "dark" theme
        mapSLayers.put(x, y, c, lerpFloatColors(foreground, map.colorLighting[1][x][y], map.colorLighting[0][x][y]), RememberedTile.memoryColorFloat); // "dark" theme
    }

    /**
     * Draws the map, applies any highlighting for the path to the cursor, and then draws the player.
     */
    public void putCrawlMap() {
        float time = (System.currentTimeMillis() & 0xffffffL) * 0.00125f; // if you want to adjust the speed of flicker, change the multiplier
        long time0 = Noise.longFloor(time);
        Radiance radiance;
        SColor.eraseColoredLighting(map.colorLighting);
        if((radiance = handBuilt.playerRadiance) != null)
        {
            FOV.reuseFOV(map.resistances, map.tempFOV, player.location.x, player.location.y, radiance.currentRange());
            SColor.colorLightingInto(map.tempColorLighting, map.tempFOV, radiance.color);
            SColor.mixColoredLighting(map.colorLighting, map.tempColorLighting);
        }
        for (int x = 0; x < map.width; x++) {
            for (int y = 0; y < map.height; y++) {
                if((radiance = map.contents[x][y].getAnyRadiance()) != null)
                {
                    FOV.reuseFOV(map.resistances, map.tempFOV, x, y, radiance.currentRange());
                    SColor.colorLightingInto(map.tempColorLighting, map.tempFOV, radiance.color);
                    SColor.mixColoredLighting(map.colorLighting, map.tempColorLighting);
                }
            }
        }

        // we can use either Noise.querp (quintic Hermite spline) or Noise.cerp (cubic Hermite splne); cerp is cheaper but querp seems to look better.
        // querp() is extremely close to cos(); see https://www.desmos.com/calculator/l31nflff3g for graphs. It is likely that querp performs better than cos.
        //float noise = Noise.querp(NumberTools.randomFloatCurved(time0), NumberTools.randomFloatCurved(time0 + 1L), time - time0);
        Physical creature;
        for (int x = 0; x < map.width; x++) {
            for (int y = 0; y < map.height; y++) {
                float sightAmount = (float) map.fovResult[x][y];
                if (sightAmount > 0) {
                    EpiTile tile = map.contents[x][y];
                    mapSLayers.clear(x, y, 1);
                    if ((creature = creatures.get(Coord.get(x, y))) != null) {
                        putWithLight(x, y, ' ', 0f);
                        creature.appearance.setPackedColor(lerpFloatColors(unseenCreatureColorFloat, creature.color, 0.5f + 0.35f * sightAmount));
                        if (creature.overlayAppearance != null)
                            creature.overlayAppearance.setPackedColor(lerpFloatColors(unseenCreatureColorFloat, creature.overlayColor, 0.5f + 0.35f * sightAmount));
                        mapSLayers.clear(x, y, 0);
                        if (!creature.wasSeen) { // stop auto-move if a new creature pops into view
                            awaitedMoves.clear();
                            toCursor.clear();
                        }
                        creature.wasSeen = true;
                    } else {
                        posrng.move(x, y);
                        putWithLight(x, y, tile.getSymbol(), tile.getForegroundColor());
                    }
                } else {
                    RememberedTile rt = map.remembered[x][y];
                    if (rt != null) {
                        mapSLayers.clear(x, y, 0);
                        mapSLayers.put(x, y, rt.symbol, rt.front, rt.back, 0);
                    }
                }
            }
        }

        mapSLayers.clear(player.location.x, player.location.y, 0);

        mapSLayers.clear(2);
        for (int i = 0; i < toCursor.size(); i++) {
            Coord c = toCursor.get(i);
            Direction dir;
            if (i == toCursor.size() - 1) {
                dir = Direction.NONE; // last spot shouldn't have arrow
            } else if (i == 0) {
                dir = Direction.toGoTo(player.location, c);
            } else {
                dir = Direction.toGoTo(toCursor.get(i - 1), c);
            }
            mapSLayers.put(c.x, c.y, Utilities.arrowsFor(dir).charAt(0), SColor.CW_PURPLE, null, 2);
        }
    }

    public void showFallingGameOver() {
        message("");
        message("");
        message("");
        message("");
        message("You have died.");
        message("");
        message("Try Again (t) or Quit (Shift-Q)?");

        mapInput.flush();
        mapInput.setKeyHandler(fallingGameOverKeys);
    }

    public void showFallingWin() {
        message("You have reached the Dragon's Hoard!");
        message("On the way, you gathered:");
        List<String> lines = StringKit.wrap(StringKit.join(", ", player.inventory), messageSize.gridWidth - 2);
        int start;
        for (start = 0; start < lines.size() && start < 4; start++) {
            message(lines.get(start));
        }
//        for (; start < 4; start++) {
//            message("");
//        }
        message("Try Again (t) or Quit (Shift-Q)?");

        mapInput.flush();
        mapInput.setKeyHandler(fallingGameOverKeys);
    }

    @Override
    public void render() {
        super.render();

        // standard clear the background routine for libGDX
        Gdx.gl.glClearColor(unseenColor.r, unseenColor.g, unseenColor.b, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        switch (mode) {
            case CRAWL:
                // crawl mode needs the camera to move around with the player since the playable crawl map is bigger than the view space
                mapStage.getCamera().position.x = player.appearance.getX();
                mapStage.getCamera().position.y = player.appearance.getY();
                putCrawlMap();
                break;
            case DIVE:
                if (fallingHandler.reachedGoal) {
                    paused = true;
                    pausedAt = Instant.now();
                    showFallingWin();
                    fallingHandler.reachedGoal = false;
                    fallingHandler.update();
                    break;
                }
                // this goes here; otherwise the player "skips" abruptly when coming out of pause
                fallingHandler.setCurrentDepth(fallingSLayers.gridY(fallingStage.getCamera().position.y = MathUtils.lerp(startingY, finishY,
                        (currentFallDuration + fallDuration) / timeToFall)));
                if (!paused) {
                    currentFallDuration = (unpausedAt.until(Instant.now(), ChronoUnit.MILLIS));
                    if (Instant.now().isAfter(nextInput)) {
                        fallingHandler.processInput();
                        nextInput = Instant.now().plusMillis(inputDelay);
                    }
                    if (Instant.now().isAfter(nextFall)) {
                        nextFall = Instant.now().plusMillis(fallDelay);
                        fallingHandler.fall();
                    }
                    infoHandler.updateDisplay();

                    for (Stat s : Stat.values()) {
                        if (player.stats.get(s).actual() <= 0) {
                            paused = true;
                            showFallingGameOver();
                        }
                    }
                } else {
                    fallDuration += currentFallDuration;
                    currentFallDuration = 0L;
                    unpausedAt = Instant.now();
                    fallingHandler.update();
                }
                break;
        }
        // if the user clicked, we have a list of moves to perform.
        if (!awaitedMoves.isEmpty()) {
            // this doesn't check for input, but instead processes and removes Points from awaitedMoves.
            if (!mapSLayers.hasActiveAnimations()) {
                Coord m = awaitedMoves.remove(0);
                if (!toCursor.isEmpty())
                    toCursor.remove(0);
                move(Direction.toGoTo(player.location, m));
                infoHandler.updateDisplay();
            }
        } else {
            multiplexer.process();
        }

        // the order here matters. We apply multiple viewports at different times to clip different areas.
        contextViewport.apply(false);
        contextStage.act();
        contextStage.draw();

        infoViewport.apply(false);
        infoStage.act();
        infoStage.draw();

        messageViewport.apply(false);
        messageStage.act();
        messageStage.draw();

        if (mode.equals(GameMode.CRAWL)) {
            //here we apply the other viewport, which clips a different area while leaving the message area intact.
            mapViewport.apply(false);
            mapStage.act();
            //we use a different approach here because we can avoid ending the batch by setting this matrix outside a batch
            batch.setProjectionMatrix(mapStage.getCamera().combined);
            //then we start a batch and manually draw the stage without having it handle its batch...
            batch.begin();
            mapSLayers.font.configureShader(batch);
            mapStage.getRoot().draw(batch, 1f);
            //so we can draw the actors independently of the stage while still in the same batch
            //player.appearance.draw(batch, 1.0f);
            //we still need to end
            batch.end();

            mapOverlayStage.act();
            mapOverlayStage.draw();
        } else {
            //here we apply the other viewport, which clips a different area while leaving the message area intact.
            fallingViewport.apply(false);
            fallingStage.act();
            //we use a different approach here because we can avoid ending the batch by setting this matrix outside a batch
            batch.setProjectionMatrix(fallingStage.getCamera().combined);
            //then we start a batch and manually draw the stage without having it handle its batch...
            batch.begin();
            fallingSLayers.font.configureShader(batch);
            fallingStage.getRoot().draw(batch, 1f);
            //so we can draw the actors independently of the stage while still in the same batch
            //player.appearance.draw(batch, 1.0f);
            //we still need to end
            batch.end();
        }
        //uncomment the upcoming line if you want to see how fast this can run at top speed...
        //for me, tommyettinger, on a laptop with recent integrated graphics, I get about 500 FPS.
        //this needs vsync set to false in DesktopLauncher.
        Gdx.graphics.setTitle(Gdx.graphics.getFramesPerSecond() + " FPS");
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        float currentZoomX = (float) width / totalPixelWidth();
        float currentZoomY = (float) height / totalPixelHeight();

        messageSLayers.setBounds(0, 0, currentZoomX * messageSize.pixelWidth(), currentZoomY * messageSize.pixelHeight());
        contextSLayers.setBounds(0, 0, currentZoomX * contextSize.pixelWidth(), currentZoomY * contextSize.pixelHeight());
        infoSLayers.setBounds(0, 0, currentZoomX * infoSize.pixelWidth(), currentZoomY * infoSize.pixelHeight());

        // SquidMouse turns screen positions to cell positions, and needs to be told that cell sizes have changed
        // a quirk of how the camera works requires the mouse to be offset by half a cell if the width or height is odd
        mapMouse.reinitialize(currentZoomX * mapSize.cellWidth, currentZoomY * mapSize.cellHeight,
                mapSize.gridWidth, mapSize.gridHeight,
                (mapSize.gridWidth & 1) * (int) (mapSize.cellWidth * currentZoomX * -0.5f),
                (mapSize.gridHeight & 1) * (int) (mapSize.cellHeight * currentZoomY * -0.5f));
        equipmentMouse.reinitialize(currentZoomX * mapSize.cellWidth, currentZoomY * mapSize.cellHeight,
                mapSize.gridWidth, mapSize.gridHeight,
                (mapSize.gridWidth & 1) * (int) (mapSize.cellWidth * currentZoomX * -0.5f),
                (mapSize.gridHeight & 1) * (int) (mapSize.cellHeight * currentZoomY * -0.5f));
        contextMouse.reinitialize(currentZoomX * contextSize.cellWidth, currentZoomY * contextSize.cellHeight,
                contextSize.gridWidth, contextSize.gridHeight,
                (contextSize.gridWidth & 1) * (int) (contextSize.cellWidth * currentZoomX * 0.5f) - (int) (messageSLayers.getRight()),
                (contextSize.gridHeight & 1) * (int) (contextSize.cellHeight * currentZoomY * 0.5f) - (int) (infoSLayers.getTop() + infoSize.cellHeight * currentZoomY));
        infoMouse.reinitialize(currentZoomX * infoSize.cellWidth, currentZoomY * infoSize.cellHeight,
                infoSize.gridWidth, infoSize.gridHeight,
                (infoSize.gridWidth & 1) * (int) (infoSize.cellWidth * currentZoomX * 0.5f) - (int) (messageSLayers.getRight()),
                (~infoSize.gridHeight & 1) * (int) (infoSize.cellHeight * currentZoomY * -0.5f));
// - (int) (infoSize.cellHeight * currentZoomY)
        contextViewport.update(width, height, false);
        contextViewport.setScreenBounds((int) (currentZoomX * mapSize.pixelWidth()), 0,
                (int) (currentZoomX * contextSize.pixelWidth()), (int) (currentZoomY * contextSize.pixelHeight()));

        infoViewport.update(width, height, false);
        infoViewport.setScreenBounds((int) (currentZoomX * mapSize.pixelWidth()), (int) (currentZoomY * contextSize.pixelHeight()),
                (int) (currentZoomX * infoSize.pixelWidth()), (int) (currentZoomY * infoSize.pixelHeight()));

        messageViewport.update(width, height, false);
        messageViewport.setScreenBounds(0, 0,
                (int) (currentZoomX * messageSize.pixelWidth()), (int) (currentZoomY * messageSize.pixelHeight()));

        mapViewport.update(width, height, false);
        mapViewport.setScreenBounds(0, (int) (currentZoomY * messageSize.pixelHeight()),
                width - (int) (currentZoomX * infoSize.pixelWidth()), height - (int) (currentZoomY * messageSize.pixelHeight()));

        mapOverlayViewport.update(width, height, false);
        mapOverlayViewport.setScreenBounds(0, (int) (currentZoomY * messageSize.pixelHeight()),
                width - (int) (currentZoomX * infoSize.pixelWidth()), height - (int) (currentZoomY * messageSize.pixelHeight()));

        fallingViewport.update(width, height, false);
        fallingViewport.setScreenBounds(0, (int) (currentZoomY * messageSize.pixelHeight()),
                width - (int) (currentZoomX * infoSize.pixelWidth()), height - (int) (currentZoomY * messageSize.pixelHeight()));
    }

    @Override
    public void pause() {
        System.out.println("Pausing game.");
        super.pause();
    }

    @Override
    public void resume() {
        System.out.println("Resuming game.");
        super.resume();
    }

    @Override
    public void dispose() {
        System.out.println("Disposing game.");
        super.dispose();
    }

    public static int totalPixelWidth() {
        return mapSize.pixelWidth() + infoSize.pixelWidth();
    }

    public static int totalPixelHeight() {
        return mapSize.pixelHeight() + messageSize.pixelHeight();
    }

    private final KeyHandler mapKeys = new KeyHandler() {
        @Override
        public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
            if (multiplexer.processedInput) return;
            int combined = SquidInput.combineModifiers(key, alt, ctrl, shift);
            if (combined == (0x60000 | SquidInput.BACKSPACE)) // ctrl-shift-backspace
            {
                multiplexer.processedInput = true;
                startGame();
                return;
            }
            Verb verb = ControlMapping.allMappings.get(combined);
            if (!ControlMapping.defaultMapViewMapping.contains(verb)) {
                return;
            }
            switch (verb) {
                case MOVE_DOWN:
                    scheduleMove(Direction.DOWN);
                    break;
                case MOVE_UP:
                    scheduleMove(Direction.UP);
                    break;
                case MOVE_LEFT:
                    scheduleMove(Direction.LEFT);
                    break;
                case MOVE_RIGHT:
                    scheduleMove(Direction.RIGHT);
                    break;
                case MOVE_DOWN_LEFT:
                    scheduleMove(Direction.DOWN_LEFT);
                    break;
                case MOVE_DOWN_RIGHT:
                    scheduleMove(Direction.DOWN_RIGHT);
                    break;
                case MOVE_UP_LEFT:
                    scheduleMove(Direction.UP_LEFT);
                    break;
                case MOVE_UP_RIGHT:
                    scheduleMove(Direction.UP_RIGHT);
                    break;
                case MOVE_LOWER:
                    // up '≤', down '≥'
                    if(map.contents[player.location.x][player.location.y].getSymbol() == '≥')
                        changeLevel(++depth);
                    //prepFall();
                    break;
                case MOVE_HIGHER:
                    // up '≤', down '≥'
                    if(map.contents[player.location.x][player.location.y].getSymbol() == '≤')
                        changeLevel(--depth);
                    break;
                case OPEN: // Open all the doors nearby
                    message("Opening nearby doors");
                    Arrays.stream(Direction.OUTWARDS)
                            .map(d -> player.location.translate(d))
                            .filter(c -> map.inBounds(c))
                            .filter(c -> map.fovResult[c.x][c.y] > 0)
                            .flatMap(c -> map.contents[c.x][c.y].contents.stream())
                            .filter(p -> p.countsAs(handBuilt.baseClosedDoor))
                            .forEach(p -> RecipeMixer.applyModification(p, handBuilt.openDoor));
                    calcFOV(player.location.x, player.location.y);
                    calcDijkstra();
                    break;
                case SHUT: // Close all the doors nearby
                    message("Closing nearby doors");
                    Arrays.stream(Direction.OUTWARDS)
                            .map(d -> player.location.translate(d))
                            .filter(c -> map.inBounds(c))
                            .filter(c -> map.fovResult[c.x][c.y] > 0)
                            .flatMap(c -> map.contents[c.x][c.y].contents.stream())
                            .filter(p -> p.countsAs(handBuilt.baseOpenDoor))
                            .forEach(p -> RecipeMixer.applyModification(p, handBuilt.closeDoor));
                    calcFOV(player.location.x, player.location.y);
                    calcDijkstra();
                    break;
                case GATHER: // Pick everything nearby up
                    message("Picking up all nearby small things");
                    for (Direction dir : Direction.values()) {
                        Coord c = player.location.translate(dir);
                        if (map.inBounds(c) && map.fovResult[c.x][c.y] > 0) {
                            EpiTile tile = map.contents[c.x][c.y];
                            ListIterator<Physical> it = tile.contents.listIterator();
                            Physical p;
                            while (it.hasNext()) {
                                p = it.next();
                                if (p.attached || p.creatureData != null) {
                                    continue;
                                }
                                player.addToInventory(p);
                                it.remove();
                            }
                        }
                    }
                    break;
                case EQUIPMENT:
                    mapOverlayHandler.setMode(PrimaryMode.EQUIPMENT);
                    mapInput.setKeyHandler(equipmentKeys);
                    toCursor.clear();
                    mapInput.setMouse(equipmentMouse);
                    break;
                case DRAW:
                    equipItem();
                    break;
                case DROP:
                    message("Dropping all held items");
                    for (Physical dropped : player.unequip(BOTH)) {
                        for (int i = 0, offset = player.next(3); i < 8; i++) {
                            Coord c = player.location.translate(Direction.OUTWARDS[i + offset & 7]);
                            if (map.inBounds(c) && map.fovResult[c.x][c.y] > 0) {
                                map.contents[c.x][c.y].add(dropped);
                                break;
                            }
                        }
                    }
                    break;
                case CONTEXT_PRIOR:
                    contextHandler.prior();
                    break;
                case CONTEXT_NEXT:
                    contextHandler.next();
                    break;
                case INFO_PRIOR:
                    infoHandler.prior();
                    break;
                case INFO_NEXT:
                    infoHandler.next();
                    break;
                case MESSAGE_PRIOR:
                    scrollMessages(-1);
                    break;
                case MESSAGE_NEXT:
                    scrollMessages(1);
                    break;
                case HELP:
                    mapOverlayHandler.setMode(PrimaryMode.HELP);
                    mapInput.setKeyHandler(helpKeys);
                    toCursor.clear();
                    mapInput.setMouse(helpMouse);
                    break;
                case QUIT:
                    // TODO - confirmation
                    Gdx.app.exit();
                    return;
                case WAIT:
                    scheduleMove(Direction.NONE);
                    break;
                default:
                    //message("Can't " + verb.name + " from main view.");
                    return;
            }
            multiplexer.processedInput = true;
            infoHandler.updateDisplay();
            // check if the turn clock needs to run
            if (verb.isAction()) {
                runTurn();
            }
        }
    };

    private final KeyHandler fallbackKeys = new KeyHandler() {
        @Override
        public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
            if (multiplexer.processedInput) return;
            Verb verb = ControlMapping.allMappings.get(SquidInput.combineModifiers(key, alt, ctrl, shift));
            String m;
            if (mapOverlaySLayers.isVisible()) {
                switch (mapOverlayHandler.getMode()) {
                    case HELP:
                        if (!ControlMapping.defaultHelpViewMapping.contains(verb)) verb = null;
                        m = "help";
                        break;
                    case CRAFTING:
                        if (!ControlMapping.defaultEquipmentViewMapping.contains(verb)) verb = null;
                        m = "crafting";
                        break;
                    default:
                        if (!ControlMapping.defaultEquipmentViewMapping.contains(verb)) verb = null;
                        m = "equipment";
                        break;
                }
            } else {
                switch (mode) {
                    case DIVE:
                        if (!ControlMapping.defaultFallingViewMapping.contains(verb)) verb = null;
                        m = "dive";
                        break;
                    default:
                        if (!ControlMapping.defaultMapViewMapping.contains(verb)) verb = null;
                        m = "map";
                        break;
                }
            }
            if (verb == null) {
                message("Unknown input for " + m + " mode: " + key);
            } else
                message("Can't " + verb.name + " from " + m + " mode.");
        }
    };

    private final KeyHandler equipmentKeys = new KeyHandler() {
        @Override
        public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
            if (multiplexer.processedInput) return;
            int combined = SquidInput.combineModifiers(key, alt, ctrl, shift);
            Verb verb = ControlMapping.allMappings.get(combined);
            if (!ControlMapping.defaultEquipmentViewMapping.contains(verb)) {
                return;
            }
            switch (verb) {
                case MOVE_DOWN:
                    mapOverlayHandler.move(Direction.DOWN);
                    break;
                case MOVE_UP:
                    mapOverlayHandler.move(Direction.UP);
                    break;
                case MOVE_LEFT:
                    mapOverlayHandler.move(Direction.LEFT);
                    break;
                case MOVE_RIGHT:
                    mapOverlayHandler.move(Direction.RIGHT);
                    break;
                case DRAW:
                    equipItem(mapOverlayHandler.getSelected());
                    mapOverlayHandler.updateDisplay();
                    break;
                case INTERACT:
                    Physical selected = mapOverlayHandler.getSelected();
                    if (selected.interactableData != null && !selected.interactableData.isEmpty()) {
                        message("Interactions for " + selected.name + ": " + selected.interactableData
                                .stream()
                                .map(interact -> interact.phrasing)
                                .collect(Collectors.joining(", ")));
                        Interactable interaction = selected.interactableData.get(0);
                        if (interaction.consumes) {
                            player.removeFromInventory(selected);
                        }
                        interaction.actorModifications.forEach(mod -> RecipeMixer.applyModification(player, mod));
                        interaction.targetModifications.forEach(mod -> RecipeMixer.applyModification(selected, mod));
                        mapOverlayHandler.updateDisplay();
                    } else if (selected.countsAs(handBuilt.rawMeat)) {
                        player.removeFromInventory(selected);
                        List<Physical> steaks = RecipeMixer.mix(handBuilt.steakRecipe, Collections.singletonList(selected), Collections.emptyList());
                        player.inventory.addAll(steaks);
                        mapOverlayHandler.updateDisplay();
                        message("Made " + steaks.size() + " steaks.");
                    } else {
                        message("No interaction for " + selected.name);
                    }
                    break;
                case MESSAGE_PRIOR:
                    scrollMessages(-1);
                    break;
                case MESSAGE_NEXT:
                    scrollMessages(1);
                    break;
                case CONTEXT_PRIOR:
                    contextHandler.prior();
                    break;
                case CONTEXT_NEXT:
                    contextHandler.next();
                    break;
                case INFO_PRIOR:
                    infoHandler.prior();
                    break;
                case INFO_NEXT:
                    infoHandler.next();
                    break;
                case HELP:
                    mapOverlayHandler.setMode(PrimaryMode.HELP);
                    mapInput.setKeyHandler(helpKeys);
                    mapInput.setMouse(helpMouse);
                    break;
                case EQUIPMENT:
                case CLOSE_SCREEN:
                    mapInput.setKeyHandler(mapKeys);
                    mapInput.setMouse(mapMouse);
                    mapOverlayHandler.hide();
                    break;
                default:
                    //message("Can't " + verb.name + " from equipment view.");
                    return; // note, this will not change processedInput
            }
            multiplexer.processedInput = true;
            infoHandler.updateDisplay();
        }
    };

    private final KeyHandler helpKeys = new KeyHandler() {
        @Override
        public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
            if (multiplexer.processedInput) return;
            int combined = SquidInput.combineModifiers(key, alt, ctrl, shift);
            Verb verb = ControlMapping.allMappings.get(combined);
            if (!ControlMapping.defaultHelpViewMapping.contains(verb)) {
                return;
            }
            switch (verb) {
                case MOVE_DOWN:
                    mapOverlayHandler.move(Direction.DOWN);
                    break;
                case MOVE_UP:
                    mapOverlayHandler.move(Direction.UP);
                    break;
                case MOVE_LEFT:
                    mapOverlayHandler.move(Direction.LEFT);
                    break;
                case MOVE_RIGHT:
                    mapOverlayHandler.move(Direction.RIGHT);
                    break;
                case MESSAGE_PRIOR:
                    scrollMessages(-1);
                    break;
                case MESSAGE_NEXT:
                    scrollMessages(1);
                    break;
                case CONTEXT_PRIOR:
                    contextHandler.prior();
                    break;
                case CONTEXT_NEXT:
                    contextHandler.next();
                    break;
                case INFO_PRIOR:
                    infoHandler.prior();
                    break;
                case INFO_NEXT:
                    infoHandler.next();
                    break;
                case EQUIPMENT:
                    mapOverlayHandler.setMode(PrimaryMode.EQUIPMENT);
                    mapInput.setKeyHandler(equipmentKeys);
                    mapInput.setMouse(equipmentMouse);
                    break;
                case HELP:
                case CLOSE_SCREEN:
                    mapInput.setKeyHandler(mapKeys);
                    mapInput.setMouse(mapMouse);
                    mapOverlayHandler.hide();
                    break;
                default:
                    //message("Can't " + verb.name + " from help view.");
                    return;
            }
            multiplexer.processedInput = true;
            infoHandler.updateDisplay();
        }
    };

    private final KeyHandler fallingKeys = new KeyHandler() {
        @Override
        public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
            if (multiplexer.processedInput) return;
            int combined = SquidInput.combineModifiers(key, alt, ctrl, shift);
            Verb verb = ControlMapping.allMappings.get(combined);
            if (!ControlMapping.defaultFallingViewMapping.contains(verb)) {
                return;
            }
            switch (verb) {
                case MOVE_UP:
                    if (!paused) {
                        nextInput = Instant.now().plusMillis(inputDelay);
                        fallingHandler.move(Direction.UP);
                    }
                    break;
                case MOVE_DOWN:
                    if (!paused) {
                        nextInput = Instant.now().plusMillis(inputDelay);
                        fallingHandler.move(Direction.DOWN);
                    }
                    break;
                case MOVE_LEFT:
                    if (!paused) {
                        nextInput = Instant.now().plusMillis(inputDelay);
                        fallingHandler.move(Direction.LEFT);
                    }
                    break;
                case MOVE_RIGHT:
                    if (!paused) {
                        nextInput = Instant.now().plusMillis(inputDelay);
                        fallingHandler.move(Direction.RIGHT);
                    }
                    break;
                case PAUSE:
                    paused = !paused;
                    if (paused) {
                        pausedAt = Instant.now();
                        message("You are hovering, have a look around!");
                    } else { // need to calculate time offsets
                        long pausedFor = pausedAt.until(Instant.now(), ChronoUnit.MILLIS);
                        nextFall = nextFall.plusMillis(pausedFor);
                        message("Falling once more!");
                    }
                    break;
                case SAVE:
                    // TODO
                    break;
                case QUIT:
                    Gdx.app.exit();
                    break;
                default:
                    //message("Can't " + verb.name + " from falling view.");
                    return;
            }
            multiplexer.processedInput = true;
        }
    };

    private final KeyHandler fallingGameOverKeys = new KeyHandler() {
        @Override
        public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
            if (multiplexer.processedInput) return;
            int combined = SquidInput.combineModifiers(key, alt, ctrl, shift);
            Verb verb = ControlMapping.allMappings.get(combined);
            if (!ControlMapping.defaultFallingViewGameOverMapping.contains(verb)) {
                return;
            }
            switch (verb) {
                case TRY_AGAIN:
                    initPlayer();
                    prepFall();
                    break;
                case QUIT:
                    Gdx.app.exit();
                    break;
                default:
                    //message("Can't " + verb.name + " from falling view.");
                    return;
            }
            multiplexer.processedInput = true;
        }
    };

    private final KeyHandler debugKeys = new KeyHandler() {
        @Override
        public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
            if (multiplexer.processedInput) return;
            switch (key) {
                case 'x':
                    fxHandler.sectorBlast(player.location, Element.ACID, 7, Radius.CIRCLE);
                    break;
                case 'X':
                    Element e = rng.getRandomElement(Element.allEnergy);
                    fxHandler.zapBoom(player.location, player.location.translateCapped(rng.between(-20, 20), rng.between(-10, 10), map.width, map.height), e);
                    break;
                case 'z':
                    fxHandler.fritz(player.location, Element.ICE, 7, Radius.CIRCLE);
                    break;
                case 'Z':
                    for (Coord c : rng.getRandomUniqueCells(0, 0, mapSize.gridWidth, mapSize.gridHeight, 400)) {
                        fxHandler.twinkle(c, Element.LIGHT);
                    }
                    break;
                case '=':
                    fxHandler.layeredSparkle(player.location, 4, Radius.CIRCLE);
                    break;
                case '+':
                    fxHandler.layeredSparkle(player.location, 8, Radius.CIRCLE);
                    break;
                default:
                    return;
            }
            multiplexer.processedInput = true;
        }
    };

    private final SquidMouse equipmentMouse = new SquidMouse(mapSize.cellWidth, mapSize.cellHeight, mapSize.gridWidth, mapSize.gridHeight, 0, 0, new InputAdapter() {

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            return false; // No-op for now
        }
    });

    private final SquidMouse helpMouse = new SquidMouse(mapSize.cellWidth, mapSize.cellHeight, mapSize.gridWidth, mapSize.gridHeight, 0, 0, new InputAdapter() {

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            return false; // No-op for now
        }
    });

    private final SquidMouse fallingMouse = new SquidMouse(mapSize.cellWidth, mapSize.cellHeight, mapSize.gridWidth, mapSize.gridHeight, 0, 0, new InputAdapter() {

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            return false; // No-op for now
        }
    });

    private final SquidMouse mapMouse = new SquidMouse(mapSize.cellWidth, mapSize.cellHeight, mapSize.gridWidth, mapSize.gridHeight, 0, 0, new InputAdapter() {
        // if the user clicks within FOV range and there are no awaitedMoves queued up, generate toCursor if it
        // hasn't been generated already by mouseMoved, then copy it over to awaitedMoves.
        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            screenX += player.location.x - (mapSize.gridWidth >> 1);
            screenY += player.location.y - (mapSize.gridHeight >> 1);
            if (screenX < 0 || screenY < 0 || screenX >= map.width || screenY >= map.height
                    || (!showingMenu && map.fovResult[screenX][screenY] <= 0.0)) {
                return false;
            }
            if(showingMenu)
            {
                if(menuLocation.x <= screenX && menuLocation.y <= screenY && screenY - menuLocation.y < attackOptions.size()
                        && currentTarget != null  && mapSLayers.getLayer(3).getFloat(screenX, screenY, 0f) != 0f)
                {
                    attack(currentTarget, attackOptions.get(screenY - menuLocation.y));
                    calcFOV(player.location.x, player.location.y);
                    calcDijkstra();
                    runTurn();
                }
                showingMenu = false;
                menuLocation = null;
                attackOptions.clear();
                currentTarget = null;
                mapSLayers.clear(3);
                mapSLayers.clear(4);
                return true;
            }
            switch (button) {
                case Input.Buttons.LEFT:
                    if (awaitedMoves.isEmpty()) {
                        if (toCursor.isEmpty()) {
                            cursor = Coord.get(screenX, screenY);
                            toPlayerDijkstra.findPathPreScanned(toCursor, cursor);
                            if (!toCursor.isEmpty()) {
                                toCursor.remove(0); // Remove cell you're in from list
                            }
                        }
                        awaitedMoves.addAll(toCursor);
                        return true;
                    }
                    break;
                case Input.Buttons.RIGHT:
                    Physical thing = map.contents[screenX][screenY].getCreature();
                    if(thing == null)
                        return false;
                    validAttackOptions(thing);
                    menuLocation = showAttackOptions(thing, attackOptions);
//                    attack(thing);
//                    calcFOV(player.location.x, player.location.y);
//                    calcDijkstra();
//                    runTurn();

                    break;
            }
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            return mouseMoved(screenX, screenY);
        }

        // causes the path to the mouse position to become highlighted (toCursor contains a list of points that
        // receive highlighting). Uses DijkstraMap.findPath() to find the path, which is surprisingly fast.
        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            if (!awaitedMoves.isEmpty()) {
                return false;
            }
            screenX += player.location.x - (mapSize.gridWidth >> 1);
            screenY += player.location.y - (mapSize.gridHeight >> 1);

            // Check if the cursor didn't move in grid space
            if (cursor.x == screenX && cursor.y == screenY) {
                return false;
            }

            if (screenX < 0 || screenX >= map.width || screenY < 0 || screenY >= map.height || map.fovResult[screenX][screenY] <= 0.0) {
                toCursor.clear(); // don't show path when mouse moves out of range or view
                return false;
            }
            contextHandler.tileContents(screenX, screenY, map.contents[screenX][screenY]);
            cursor = Coord.get(screenX, screenY);
            toCursor.clear();
            toPlayerDijkstra.findPathPreScanned(toCursor, cursor);
            if (!toCursor.isEmpty()) {
                toCursor.remove(0);
            }
            return false;
        }
    });
    
    private final SquidMouse contextMouse = new SquidMouse(contextSize.cellWidth, contextSize.cellHeight, contextSize.gridWidth, contextSize.gridHeight,
            mapSize.gridWidth * mapSize.cellWidth, infoSize.gridHeight * infoSize.cellHeight + (infoSize.cellHeight >> 1), new InputAdapter() {
        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (screenX < 0 || screenX >= contextSize.gridWidth || screenY < 0 || screenY >= contextSize.gridHeight) {
                return false;
            }
            switch (button) {
                case Input.Buttons.LEFT:
                    if (screenX == contextHandler.arrowLeft.x && screenY == contextHandler.arrowLeft.y) {
                        contextHandler.prior();
                    } else if (screenX == contextHandler.arrowRight.x && screenY == contextHandler.arrowRight.y) {
                        contextHandler.next();
                    }
                    return true;
                case Input.Buttons.RIGHT:
                default:
                    return false;
            }
        }


        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            return mouseMoved(screenX, screenY);
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            return false;
        }
    });

    private final SquidMouse infoMouse = new SquidMouse(infoSize.cellWidth, infoSize.cellHeight, infoSize.gridWidth, infoSize.gridHeight,
            mapSize.gridWidth * mapSize.cellWidth, contextSize.gridHeight * contextSize.cellHeight, new InputAdapter() {
        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            //System.out.println("info: " + screenX + ", " + screenY);
            switch (button) {
                case Input.Buttons.LEFT:
                    if (screenX == infoHandler.arrowLeft.x && screenY == infoHandler.arrowLeft.y) {
                        infoHandler.prior();
                    } else if (screenX == infoHandler.arrowRight.x && screenY == infoHandler.arrowRight.y) {
                        infoHandler.next();
                    }
                    return true;
                case Input.Buttons.RIGHT:
                default:
                    return false;
            }
        }


        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            return mouseMoved(screenX, screenY);
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            return false;
        }
    });
}
