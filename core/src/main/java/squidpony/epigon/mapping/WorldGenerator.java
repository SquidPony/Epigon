package squidpony.epigon.mapping;

import squidpony.StringKit;
import squidpony.epigon.data.*;
import squidpony.epigon.data.quality.Inclusion;
import squidpony.epigon.data.quality.Stone;
import squidpony.epigon.data.quality.Vegetable;
import squidpony.epigon.data.trait.Grouping;
import squidpony.epigon.playground.HandBuilt;
import squidpony.squidgrid.gui.gdx.SColor;
import squidpony.squidgrid.mapping.DenseRoomMapGenerator;
import squidpony.squidgrid.mapping.DungeonGenerator;
import squidpony.squidgrid.mapping.FlowingCaveGenerator;
import squidpony.squidgrid.mapping.SerpentMapGenerator;
import squidpony.squidgrid.mapping.styled.DungeonBoneGen;
import squidpony.squidgrid.mapping.styled.TilesetType;
import squidpony.squidmath.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates a world.
 *
 * @author Eben Howard - http://squidpony.com
 */
public class WorldGenerator {

    public static final OrderedMap<Character, EnumOrderedSet<Vegetable>> vegetablesByTerrain = new OrderedMap<>(8);
    private static final int maxRecurse = 10;
    private EpiMap[] world;
    private int width, height, depth;
    private HandBuilt handBuilt;
    private StatefulRNG rng;
    private Map<Stone, Physical> walls = new EnumMap<>(Stone.class);
    private Map<Stone, Physical> floors = new EnumMap<>(Stone.class);
    
    public WorldGenerator()
    {
        Vegetable[] vegetables = Vegetable.ALL;
        for (int v = 0; v < vegetables.length; v++) {
            String terrains = vegetables[v].terrains();
            for (int i = 0; i < terrains.length(); i++) {
                if (!vegetablesByTerrain.containsKey(terrains.charAt(i)))
                    vegetablesByTerrain.put(terrains.charAt(i), new EnumOrderedSet<>(vegetables[v]));
                vegetablesByTerrain.get(terrains.charAt(i)).add(vegetables[v]);
            }
        }
    }

    public EpiMap[] buildCastle(int width, int height, int depth, int sky, HandBuilt handBuilt) {
        EpiMap[] underground = buildWorld(width, height, depth, handBuilt);
        EpiMap[] aboveground = new EpiMap[sky + 1]; // first layer above ground is floor zero

        for (int i = 0; i <= sky; i++) {
            aboveground[i] = new EpiMap(width, height);
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                aboveground[sky].contents[x][y] = new EpiTile(RecipeMixer.buildPhysical(Physical.makeBasic("stone walkway", '.', SColor.LINEN)));
            }
        }

        generateCastle(aboveground);

        world = Stream.of(aboveground, underground).flatMap(Stream::of).toArray(EpiMap[]::new);

        return world;
    }

    public EpiMap buildDive(int width, int depth, HandBuilt handBuilt) {

        world = buildWorld(width, 6, depth, handBuilt);

        this.width = width;
        this.height = depth + World.DIVE_HEADER.length;
        this.depth = 1;
        EpiMap map = new EpiMap(width, height);
        GreasedRegion safeSpots = new GreasedRegion(width, height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < depth; y++) {
                map.contents[x][y + World.DIVE_HEADER.length] = world[y].contents[x][0];
            }
        }

        // Add in dive header
        for (int x = 0; x < World.DIVE_HEADER[0].length(); x++) {
            for (int y = 0; y < World.DIVE_HEADER.length; y++) {
                char c = World.DIVE_HEADER[y].charAt(x);
                switch (c) {
                    case ' ':
                        map.contents[x][y] = new EpiTile(handBuilt.emptySpace);
                        break;
                    case '$':
                        map.contents[x][y] = new EpiTile(handBuilt.emptySpace);
                        map.contents[x][y].add(handBuilt.money);
                        break;
                    default:
                        Physical p = new Physical();
                        p.symbol = c;
                        p.color = SColor.SCARLET.toFloatBits();
                        p.blocking = true;
                        map.contents[x][y] = new EpiTile(p);
                        break;
                }
            }
        }

        int centerGap = width / 2;
        int gapSize = (int) (width * 0.4);
        long seed1 = handBuilt.rng.nextLong() + System.nanoTime(),
            seed2 = handBuilt.rng.nextLong() + seed1,
            seed3 = handBuilt.rng.nextLong() + seed2 ^ seed1;
        final double portionGapSize = 0.08 * width, offGapSize = 0.12 * width,
            halfWidth = 0.5 * width, centerOff = 0.135 * width, extraWiggle = 0.02 * width;
        for (int level = World.DIVE_HEADER.length; level < height; level++) {
            for (int x = centerGap - gapSize; x < centerGap + gapSize; x++) {
                map.contents[x][level].floor = handBuilt.emptySpace;
                map.contents[x][level].blockage = null;
                safeSpots.insert(x, level);
            }
            // Basic1D noise is more wobbly, with small changes frequently and frequent (cyclical) major changes
            gapSize = (int) (Noise.Basic1D.noise(level * 0.17, seed1) * portionGapSize + offGapSize
                + NumberTools.randomFloatCurved(seed3 * (level + seed2)) * extraWiggle);
            // swayRandomized spends a little more time at extremes before shifting suddenly to a very different value
            centerGap = (int) ((NumberTools.swayRandomized(seed2, level * 0.08) + NumberTools.swayRandomized(seed3, level * 0.135)) * centerOff + halfWidth);
            centerGap = Math.max(centerGap, gapSize / 2 + 1); // make sure it's not off the left side
            centerGap = Math.min(centerGap, width - gapSize / 2 - 1); // make sure it's not off the right side
        }
        rng = new StatefulRNG(new DiverRNG(handBuilt.rng.nextLong() ^ seed3));
        safeSpots.retract(2).randomScatter(rng, 8);

        Inclusion[] inclusions = Inclusion.values();
        Physical[] contents = new Physical[inclusions.length + 1];
        double[] weights = new double[inclusions.length + 1];
        for (int i = 0; i < inclusions.length; i++) {
            Physical gem = RecipeMixer.buildPhysical(inclusions[i]);
            gem.symbol = '♦';
            gem.groupingData = new Grouping(1);
            contents[i] = gem;
            weights[i] = rng.between(1.0, 3.0);
        }
        contents[inclusions.length] = handBuilt.money;
        weights[inclusions.length] = inclusions.length * 3.25;
        WeightedTableWrapper<Physical> table = new WeightedTableWrapper<>(rng.nextLong(), contents, weights);

        for (Coord cash : safeSpots) {
            if (map.contents[cash.x][cash.y].blockage == null) {
                map.contents[cash.x][cash.y].add(table.random());
            }
        }

        // Close off bottom with "goal"
        Physical goal = new Physical();
        goal.color = SColor.GOLDEN.toFloatBits();
        goal.symbol = '♥';
        goal.blocking = false;
        goal.unique = true; // misusing this intentionally to mark special "objects"
        for (int x = 0; x < width; x++) {
            map.contents[x][height - 2].floor = goal;
            map.contents[x][height - 2].add(RecipeMixer.buildPhysical(goal));
            map.contents[x][height - 2].blockage = null;
            map.contents[x][height - 1].floor = goal;
            map.contents[x][height - 1].add(RecipeMixer.buildPhysical(goal));
            map.contents[x][height - 1].blockage = null;
        }

        return map;
    }

    public EpiMap[] buildWorld(int width, int height, int depth, HandBuilt handBuilt) {
        init(width, height, depth, handBuilt);
        placeMinerals();
        faultMap();
        bubbleMap(false);
        extrudeMap();
        faultMap();
        bubbleMap(false);
        intrudeMap();
        metamorphoseMap();

        makeSolid();

        EpiTile tile;
        GreasedRegion[] floorWorld = new GreasedRegion[depth];
        GreasedRegion tmp = new GreasedRegion(width, height);

        for (int e = 0; e < depth; e++) {
            EpiMap eMap = world[e];
            DungeonGenerator gen = new DungeonGenerator(width, height, rng);
            // create vertical "zones" for types of generation
            if (e < 2) {
                DenseRoomMapGenerator dense = new DenseRoomMapGenerator(width, height, rng);
                gen.addDoors(80, true);
                gen.generate(dense.generate());
            } else if (e < 4) {
                FlowingCaveGenerator flowing = new FlowingCaveGenerator(width, height, TilesetType.DEFAULT_DUNGEON, rng);
                gen.addBoulders(8);
                gen.addWater(14, 4);
                gen.addGrass(17);
                gen.generate(flowing.generate());
            } else {
                SerpentMapGenerator serpent = new SerpentMapGenerator(width, height, rng, 0.2);
                serpent.putWalledBoxRoomCarvers(4);
                serpent.putWalledRoundRoomCarvers(2);
                serpent.putCaveCarvers(1);
                gen.generate(serpent.generate());
            }

            char[][] dungeonChars = gen.getDungeon();
            floorWorld[e] = new GreasedRegion(gen.getBareDungeon(), '.');

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    char c = dungeonChars[x][y];
                    tile = eMap.contents[x][y];
                    tile.blockage = null;
                    switch (c) {
                        case '.':
                            break;
                        case '#':
                            placeWall(tile);
                            break;
                        case '+':
                        case '/':
                            placeDoor(tile);
                            break;
                        case '~': // TODO - distinguish deep water
                        case ',':
                            placeWater(tile);
                            break;
                        default:
                            tile.floor = RecipeMixer.buildPhysical(tile.floor); // Copy out the old floor before modifying it
                            tile.floor.symbol = eMap.altSymbolOf(c);
                            tile.floor.color = eMap.colorOf(c);
                            tile.floor.name = "modified " + c;
                            break;
                    }
                    if(vegetablesByTerrain.containsKey(tile.floor.symbol) && tile.floor.next(5) == 0)
                        // 1 in 32 chance
                        tile.contents.add(RecipeMixer.buildVegetable(vegetablesByTerrain.get(tile.floor.symbol).randomItem(tile.floor)));

                }
            }
        }
        for (int e = 0; e < depth - 1; e++) {
            EpiMap eMap = world[e];
            EpiMap nextMap = world[e + 1];
            tmp.remake(floorWorld[e]).and(floorWorld[e + 1]).randomScatter(rng, 21, 4);
            eMap.downStairPositions.or(tmp);
            nextMap.upStairPositions.or(tmp);
            for (Coord c : tmp) {
                placeStairs(eMap, nextMap, c);
            }
            floorWorld[e].andNot(tmp);
            floorWorld[e + 1].andNot(tmp);
        }
        for (int e = 1; e < depth; e++) {
            EpiMap eMap = world[e];
            EpiMap prevMap = world[e - 1];
            tmp.remake(floorWorld[e]).and(floorWorld[e - 1]).randomScatter(rng, 21, 4);
            eMap.upStairPositions.or(tmp);
            prevMap.downStairPositions.or(tmp);
            for (Coord c : tmp) {
                placeStairs(prevMap, eMap, c);
            }
            floorWorld[e].andNot(tmp);
            floorWorld[e - 1].andNot(tmp);
        }

        return world;
    }

    private void init(int width, int height, int depth, HandBuilt handBuilt) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.handBuilt = handBuilt;
        rng = handBuilt.rng.copy();
        rng.setState(1000L);
        world = new EpiMap[depth];

        for (int d = 0; d < depth; d++) {
            world[d] = new EpiMap(width, height);
        }
    }

    private void placeStairs(EpiMap top, EpiMap bottom, Coord c) {
        placeStairs(top.contents[c.x][c.y], false);
        placeStairs(bottom.contents[c.x][c.y], true);
    }

    private void placeStairs(EpiTile tile, boolean up) {
        Physical adding;
        if (tile.floor != null) {
            if (tile.floor.terrainData != null && tile.floor.terrainData.stone != null) {
                adding = RecipeMixer.buildPhysical(tile.floor.terrainData.stone);
            } else {
                adding = tile.floor;
            }
        } else {
            adding = RecipeMixer.buildPhysical(Inclusion.DIAMOND); // TODO - replace with base of whatever is appropriate
        }

        if (up) {
            tile.contents.addAll(RecipeMixer.mix(handBuilt.upStairRecipe, Collections.singletonList(adding), Collections.emptyList()));
        } else {
            tile.contents.addAll(RecipeMixer.mix(handBuilt.downStairRecipe, Collections.singletonList(adding), Collections.emptyList()));
        }
    }

    private void placeDoor(EpiTile tile) {
        Physical adding = RecipeMixer.buildPhysical(tile.floor.terrainData.stone);
        List<Physical> adds = RecipeMixer.mix(handBuilt.doorRecipe, Collections.singletonList(adding), Collections.emptyList());
        Physical door = adds.get(0);
        setDoorOpen(door, rng.nextBoolean());
        tile.add(door);
    }

    /**
     * Sets the door to the open state, true means open and false means closed.
     *
     * @param open
     */
    private void setDoorOpen(Physical door, boolean open) {
        RecipeMixer.applyModification(door, open ? handBuilt.openDoor : handBuilt.closeDoor);
    }

    private void placeWall(EpiTile tile) {
        Physical adding = getWall(tile.floor.terrainData.stone);
        tile.add(adding);
    }

    private void placeWater(EpiTile tile) {
//        tile.floor = RecipeMixer.buildPhysical(handBuilt.lava);
//        tile.floor.radiance.color = SColor.lerpFloatColors(SColor.CW_ORANGE.toFloatBits(), SColor.CW_YELLOW.toFloatBits(),
//                tile.floor.nextFloat() * (tile.floor.nextFloat(0.75f) + 0.25f));
//        tile.floor.radiance.delay = tile.floor.nextFloat();
        tile.floor = RecipeMixer.buildPhysical(handBuilt.water);
    }

    private void placeMud(EpiTile tile) {
        tile.floor = RecipeMixer.buildPhysical(handBuilt.mud);
    }

    private Physical getWall(Stone stone) {
        Physical wall = walls.get(stone);
        if (wall != null) {
            return wall;
        }

        wall = RecipeMixer.buildPhysical(RecipeMixer.buildPhysical(stone));
        RecipeMixer.applyModification(wall, handBuilt.makeWall);
        walls.put(stone, wall);
        return wall;
    }

    private Physical getFloor(Stone stone) {
        Physical floor = floors.get(stone);
        if (floor != null) {
            return floor;
        }

        floor = RecipeMixer.buildPhysical(RecipeMixer.buildPhysical(stone));
        floor.name = stone.toString() + " floor";
        floors.put(stone, floor);
        return floor;
    }

    /**
     * Randomly places minerals in the provided map.
     */
    private void placeMinerals() {
        int z = 0;
        int thickness = rng.between(12, 18);
        Physical floor = getFloor(rng.getRandomElement(Stone.values()));
        while (z < depth) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    EpiTile tile = world[z].contents[x][y];
                    if (tile == null) {
                        tile = new EpiTile(floor);
                        world[z].contents[x][y] = tile;
                    }
                }
            }
            z++;
            thickness--;
            if (thickness <= 0) {
                thickness = rng.between(2, 10);
                floor = getFloor(rng.getRandomElement(Stone.values()));
            }
        }
    }

    /**
     * Makes every block contain a full wall. Should be called after floor manipulations are done to have walls match
     * the floor under them.
     */
    private void makeSolid() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    EpiTile tile = world[z].contents[x][y];
                    tile.add(getWall(tile.floor.terrainData.stone));
                }
            }
        }
    }

    private void bubbleMap(boolean useExistingFloor) {
        int quantity = (width * Integer.max(depth, height)) / 10;
        int sizeX = 16;
        int sizeY = 15;
        int sizeZ = 8;
        int centerX = 0;
        int centerY = 0;
        int centerZ = 0;
        int counter = 0;
        Physical blueprint = null;
        for (int growStep = 0; growStep < quantity; growStep++) {
            if (counter <= 0) {
                counter = rng.nextInt(3);
                centerX = rng.nextInt(width);
                centerY = rng.nextInt(height);
                centerZ = rng.nextInt(depth);

                if (useExistingFloor) {
                    blueprint = world[centerZ].contents[centerX][centerY].floor;
                } else {
                    blueprint = getFloor(rng.getRandomElement(Stone.values()));
                }
            }

            counter--;
            int bubbleSizeX = rng.nextInt(sizeX) + 1;//this will be the approximate size of the bubbles
            int bubbleSizeY = rng.nextInt(sizeY) + 1;//this will be the approximate size of the bubbles
            int bubbleSizeZ = rng.nextInt(sizeZ) + 2;//this will be the approximate size of the bubbles
            int bubbleGrowXStart = centerX - bubbleSizeX - rng.nextInt(2);
            int bubbleGrowXEnd = centerX + bubbleSizeX + rng.nextInt(2);
            for (int bubbleGrowZ = centerZ - bubbleSizeZ - rng.nextInt(1); bubbleGrowZ < (centerZ + bubbleSizeZ + rng.nextInt(1)); bubbleGrowZ++) {
                for (int bubbleGrowX = bubbleGrowXStart; bubbleGrowX < bubbleGrowXEnd; bubbleGrowX++) {
                    for (int bubbleGrowY = centerY - bubbleSizeY - rng.nextInt(2); bubbleGrowY < (centerY + bubbleSizeY + rng.nextInt(2)); bubbleGrowY++) {
                        if (pointInBounds(bubbleGrowX, bubbleGrowY, bubbleGrowZ)) {
                            world[bubbleGrowZ].contents[bubbleGrowX][bubbleGrowY].floor = blueprint;
                        }
                        /* mini-stagger walk here */
                        for (int m = 0; m < rng.nextInt(3); m++) {
                            int newX = bubbleGrowX - rng.between(-1, 2);//rng.nextInt(2) - 1);
                            int newY = bubbleGrowY - rng.between(-1, 2);//rng.nextInt(2) - 1);
                            int newZ = bubbleGrowZ - rng.between(-1, 2);//rng.nextInt(2) - 1);
                            if (pointInBounds(newX, newY, newZ)) {
                                world[newZ].contents[newX][newY].floor = blueprint;
                            }
                        }
                    }
                }
            }
        }
    }

    private void faultMap() {
        int x;
        int y;//y = mx+b
        int x2;
        int y2;
        double m = 1;

//        do { // single thickness does not play nice with checks against single thickness :)
        x = rng.nextInt(width);
        y = rng.nextInt(height);
        do {
            x2 = x - rng.nextInt(width);
            y2 = y - rng.nextInt(height);
        } while ((x2 == 0) && (y2 == 0));
        m = (y2) / (x2);//y - y1/x - x1
//        } while (((int) m == 0) || ((int) m == 1) || ((int) m == -1));

        int b = (int) (y - m * x);//y-mx

        for (int z = 0; z < (depth - 1); z++) {
            for (x = 1; x < (width - 1); x++) {
                for (y = 1; y < (height - 1); y++) {
                    if (y < (m * x + b)) {
                        world[z].contents[x][y].floor = world[z + 1].contents[x][y].floor;
                    }
                }
            }
        }
    }

    private void intrudeMap() {
        Stone intruder = rng.getRandomElement(Arrays
            .stream(Stone.values())
            .filter(s -> s.intrusive)
            .collect(Collectors.toList()));
        int startX = rng.nextInt(width - 2) + 1;
        int startY = rng.nextInt(height - 2) + 1;
        int startZ = rng.nextInt(depth) + depth / 2;
        startZ = Math.min(startZ, depth - 1);

        int forceZ = 10;
        int n = rng.nextInt(3);
        int n2 = rng.nextInt(2);
        switch (n) {
            case 0:
                switch (n2) {
                    case 0:
                        startX = 1;
                        break;
                    case 1:
                        startX = width - 1;
                        break;
                }
                break;
            case 1:
                switch (n2) {
                    case 0:
                        startY = 1;
                        break;
                    case 1:
                        startY = height - 1;
                        break;
                }
                break;
            case 2:
                startZ = depth - 1;
                forceZ = depth * 4 / 5;
                break;
        }

        int currentX = startX;
        int currentY = startY;
        for (int z = startZ; ((forceZ > 0) && (z >= 0)); z--) {
            int forceX = 10;
            int forceY = 5;
            for (int x = currentX - forceX; x < currentX + forceX; x++) {
                for (int y = currentY - forceY; y < currentY + forceY; y++) {
                    if (pointInBounds(x, y, z)) {
                        world[z].contents[x][y].floor = getFloor(intruder);
                    }
                    forceY += rng.nextInt(3) - 1;
                    forceX += rng.nextInt(3) - 1;
                }
            }

            forceZ -= 1;
        }
    }

    private void extrudeMap() {
        Stone extruder = rng.getRandomElement(Arrays
            .stream(Stone.values())
            .filter(s -> s.extrusive)
            .collect(Collectors.toList()));
        Physical blueprint;
        int extrudeX = -1;
        int extrudeY = -1;
        int extrudeZ = -1;

        test_for_igneous:
        for (int testZ = 0; testZ < depth; testZ++) {
            for (int n = 0; n < maxRecurse; n++) {
                extrudeX = rng.nextInt(width - 2) + 1;
                extrudeY = rng.nextInt(height - 2) + 1;
                if (pointInBounds(extrudeX, extrudeY, testZ)) {
                    blueprint = world[testZ].contents[extrudeX][extrudeY].floor;

                    if ((blueprint.terrainData.extrusive) || (blueprint.terrainData.intrusive)) {
                        extrudeZ = testZ;
                        break test_for_igneous;
                    }
                }
            }
        }

        if (pointInBounds(extrudeX, extrudeY, extrudeZ)) {
            int sizeX, sizeY;
            double n;
            for (int z = extrudeZ; z >= 0; z--) {
                sizeX = rng.nextInt(5) + 10;
                sizeY = rng.nextInt(3) + 10;
                for (int x = extrudeX - sizeX; x < extrudeX + sizeX; x++) {
                    for (int y = extrudeY - sizeY; y < extrudeY + sizeY; y++) {
                        n = (Math.pow((double) (extrudeX - x) / sizeX, 1) + Math.pow((double) (extrudeY - y) / sizeX, 1));
                        if (n < 1) {//code for oval shape
                            if (pointInBounds(x, y, z)) {
                                world[z].contents[x][y].floor = getFloor(extruder);
                            }
                        }
                    }
                }
            }
        }
    }

    private void metamorphoseMap() {
        Physical[][][] near = new Physical[3][3][3];
        Stone changer;
        int changetrack = 0;
        boolean changing, igneous, sedimentary;
        changer = rng.getRandomElement(Arrays
            .stream(Stone.values())
            .filter(s -> s.metamorphic)
            .collect(Collectors.toList()));
        for (int j = 0; j < depth; j++) {
            changetrack++;
            if (changetrack > 4) {
                changer = rng.getRandomElement(Arrays
                    .stream(Stone.values())
                    .filter(s -> s.metamorphic)
                    .collect(Collectors.toList()));
            }
            for (int i = 1; i < width - 1; i++) {
                for (int k = 1; k < height - 1; k++) {
                    changing = false;
                    igneous = false;
                    sedimentary = false;
                    for (int a = 0; a < 3; a++) {//build array of near objects
                        for (int b = 0; b < 3; b++) {
                            for (int c = 0; c < 3; c++) {
                                near[a][b][c] = pointInBounds(i + a - 1, k + b - 1, j + c - 1) ? world[j + c - 1].contents[i + a - 1][k + b - 1].floor : null;
                            }
                        }
                    }

                    test_for_change:
                    for (Physical testing1[][] : near) {
                        for (Physical testing2[] : testing1) {
                            for (Physical test : testing2) {
                                if (test == null || test.terrainData == null) {
                                    continue;
                                }
                                if (test.terrainData.sedimentary) {
                                    sedimentary = true;
                                }
                                if (test.terrainData.extrusive || test.terrainData.intrusive) {
                                    igneous = true;
                                }
                                if (sedimentary && igneous) {
                                    changing = true;
                                    break test_for_change;
                                }
                            }
                        }
                    }

                    if (changing) {
                        if (pointInBounds(i, k, j)) {
                            if (rng.nextInt(100) < 45) {
                                world[j].contents[i][k].floor = getFloor(changer); // TODO - cache
                            }
                        }
                    }
                }
            }
        }

        for (int j = depth; j > 0; j--) {
            changetrack++;
            if (changetrack > 4) {
                changer = rng.getRandomElement(Arrays
                    .stream(Stone.values())
                    .filter(s -> s.metamorphic)
                    .collect(Collectors.toList()));
            }
            for (int i = width - 1; i > 1; i--) {
                for (int k = height - 1; k > 1; k--) {
                    changing = false;
                    igneous = false;
                    sedimentary = false;
                    for (int a = 0; a < 3; a++) {//build array of near objects
                        for (int b = 0; b < 3; b++) {
                            for (int c = 0; c < 3; c++) {
                                near[a][b][c] = pointInBounds(i + a - 1, k + b - 1, j + c - 1) ? world[j + c - 1].contents[i + a - 1][k + b - 1].floor : null;
                            }
                        }
                    }

                    test_for_change:
                    for (Physical testing1[][] : near) {
                        for (Physical testing2[] : testing1) {
                            for (Physical test : testing2) {
                                if (test == null || test.terrainData == null) {
                                    continue;
                                }
                                if (test.terrainData.sedimentary) {
                                    sedimentary = true;
                                }
                                if (test.terrainData.extrusive || test.terrainData.intrusive) {
                                    igneous = true;
                                }
                                if (sedimentary && igneous) {
                                    changing = true;
                                    break test_for_change;
                                }
                            }
                        }
                    }

                    if (changing) {
                        if (pointInBounds(i, k, j)) {
                            if (rng.nextInt(100) < 25) {
                                world[j].contents[i][k].floor = getFloor(changer);
                            }
                        }
                    }
                }
            }
        }
    }

    private void generateCastle(EpiMap[] buildZone) {
        Castle castle = new Castle(buildZone);
        buildGroundLevelCastle(castle);
    }

    private void buildGroundLevelCastle(Castle castle) {
        EpiMap map = castle.buildZone[castle.ground];

        //choose area for moat
        int distance = 8; // space between points
        castle.moat.surface8way(8);
        List<Coord> corners = findInternalPolygonCorners(castle.moat, distance, 7);
        castle.moat.clear();
        castle.moat = connectPoints(castle.moat, corners);

        castle.moat.expand8way();
        castle.moatBank = castle.moat.copy();
        GreasedRegion nonMoat = castle.region.copy().andNot(castle.moat);
        for (Coord c : nonMoat) {             
            map.contents[c.x][c.y].floor = getFloor(Stone.ARGILLITE);
        }

        castle.moat.fray(0.3).fray(0.1);
        for (Coord c : castle.moat) {
            placeWater(map.contents[c.x][c.y]);
        }

        castle.moatBank.andNot(castle.moat);
        for (Coord c : castle.moatBank) {
            placeMud(map.contents[c.x][c.y]);
        }

        castle.insideMoat = new GreasedRegion(findCentroid(corners), castle.region.width, castle.region.height)
            .flood8way(nonMoat, castle.region.width * castle.region.height);

        castle.insideMoat.andNot(castle.moat).andNot(castle.moatBank);
        for (Coord c : castle.insideMoat) {
            map.contents[c.x][c.y].floor = getFloor(Stone.OBSIDIAN);
        }

        corners = findInternalPolygonCorners(castle.insideMoat, distance / 2, 4);
        Coord courtyardCentroid = findCentroid(corners);
        castle.outerWall = new GreasedRegion(courtyardCentroid, castle.insideMoat.width, castle.insideMoat.height);
        while (!castle.outerWall.intersects(castle.moat))
        {
            castle.outerWall.expand8way();
        }
        castle.outerWall.surface8way(3);
//        castle.outerWall = connectPoints(castle.outerWall, corners);
        castle.holes = castle.outerWall.copy().randomScatter(rng, 8).expand(); // find the holes before the expansion so that they're in the middle of the wall
        castle.outerWall.expand();

        for (Coord c : castle.outerWall) {
            map.contents[c.x][c.y].add(getWall(Stone.GNEISS));
            map.contents[c.x][c.y].floor = getFloor(Stone.GNEISS);
        }

        castle.courtyard = new GreasedRegion(courtyardCentroid, castle.region.width, castle.region.height)
            .flood8way(castle.insideMoat.copy().andNot(castle.outerWall), castle.region.width * castle.region.height);

        Physical brick = RecipeMixer.buildPhysical(Physical.makeBasic("brick", '≡', SColor.PERSIAN_RED));
        for (Coord c : castle.courtyard) {
            map.contents[c.x][c.y].floor = brick;
        }

        castle.holes.expand(2);
        castle.holes.fray(0.2);
        castle.holes.fray(0.2);
        Physical rubble = RecipeMixer.buildPhysical(Physical.makeBasic("rubble", ';', SColor.GREYISH_DARK_GREEN));
        for (Coord c : castle.holes) {
            map.contents[c.x][c.y].blockage = null;
            map.contents[c.x][c.y].add(rubble);
            placeMud(map.contents[c.x][c.y]);
        }

        buildKeep(castle, courtyardCentroid);

        castle.garden = castle.courtyard.copy().andNot(castle.keepWall).andNot(castle.insideKeep);
        Physical pondWater = RecipeMixer.buildPhysical(Physical.makeBasic("pond water", '~', SColor.SEA_GREEN));

        castle.pond = castle.garden.copy();
        Coord pondCenter = castle.pond.singleRandom(rng);
        castle.pond.and(castle.pond.copy().fill(false).insertCircle(pondCenter, 2));
        castle.pond.fray(0.4);
        for (Coord c : castle.pond) {
            map.contents[c.x][c.y].blockage = null;
            map.contents[c.x][c.y].floor = pondWater;
        }
        castle.pondBank = castle.pond.copy().fringe().andNot(castle.keepWall).andNot(castle.insideKeep).andNot(castle.outerWall);
        for (Coord c : castle.pondBank) {
            placeMud(map.contents[c.x][c.y]);
        }
        GreasedRegion outside = castle.region.copy().not().flood(castle.insideMoat.copy().not(), castle.width * castle.height);
        for(GreasedRegion area : new GreasedRegion[]{
                castle.garden, castle.pond, castle.pondBank, outside
        })
        {
            for(Coord c : area) {
                float noise = FastNoise.instance.getSimplex(c.x * 0.6f, c.y * 0.6f);
                if (noise > -0.1f && noise < 0.35f) {
                    if(map.contents[c.x][c.y].floor.symbol == '.') 
                        map.contents[c.x][c.y].floor = RecipeMixer.buildPhysical(handBuilt.grass);
                    if (map.contents[c.x][c.y].floor.next(3) == 0 && vegetablesByTerrain.containsKey(map.contents[c.x][c.y].floor.symbol)) {
                        // 1 in 8 chance
                        map.contents[c.x][c.y].contents.add(RecipeMixer.buildVegetable(
                                vegetablesByTerrain.get(map.contents[c.x][c.y].floor.symbol).randomItem(map.contents[c.x][c.y].floor)));
                    }
                }
            }

        }
    }

    private void buildKeep(Castle castle, Coord courtyardCentroid) {
        // sketch out a keep's borders
        
//        int top = courtyardCentroid.y;
//        int bottom = courtyardCentroid.y;
//        int left = courtyardCentroid.x;
//        int right = courtyardCentroid.x;
//        int lastChoice = 0;
//        expandKeep:
//        while (true) {
//            lastChoice = rng.nextInt(4);
//            switch (lastChoice) {
//                case 0:
//                    top--;
//                    break;
//                case 1:
//                    right++;
//                    break;
//                case 2:
//                    left--;
//                    break;
//                case 3:
//                default:
//                    bottom++;
//                    break;
//            }
//            for (int x = left; x <= right; x++) {
//                if (!castle.courtyard.contains(x, top) || !castle.courtyard.contains(x, bottom)) {
//                    break expandKeep;
//                }
//            }
//            for (int y = top; y <= bottom; y++) {
//                if (!castle.courtyard.contains(left, y) || !castle.courtyard.contains(right, y)) {
//                    break expandKeep;
//                }
//            }
//        }
//        switch (lastChoice) {
//            case 0:
//                top++;
//                break;
//            case 1:
//                right--;
//                break;
//            case 2:
//                left++;
//                break;
//            case 3:
//            default:
//                bottom--;
//                break;
//        }
        castle.insideKeep = new GreasedRegion(courtyardCentroid, castle.courtyard.width, castle.courtyard.height);
        while (!castle.insideKeep.intersects(castle.outerWall))
        {
            castle.insideKeep.expand8way();
        }
        castle.insideKeep.retract(6);
        
        castle.keepWall = connectPoints(new GreasedRegion(castle.courtyard.width, castle.courtyard.height),
                castle.insideKeep.copy().andNot(castle.insideKeep.copy().removeCorners()).asCoords());
        for (Coord c : castle.keepWall) {
            for (int z = 0; z <= 4; z++) {
                EpiTile tile = castle.tileAt(c, castle.ground - z);
                if (tile == null) {
                    tile = new EpiTile();
                    castle.setTileAt(c, castle.ground - z, tile);
                }
                tile.floor = getFloor(Stone.MARBLE);
                //tile.add(getWall(Stone.MARBLE));
            }

            if ((c.x + c.y & 1) == 0) {
                EpiTile tile = castle.tileAt(c, castle.ground - 5);
                if (tile == null) {
                    tile = new EpiTile();
                    castle.setTileAt(c, castle.ground - 5, tile);
                }
                tile.floor = getFloor(Stone.MARBLE);
                tile.add(getWall(Stone.MARBLE));
            }
        }

//        castle.insideKeep = new GreasedRegion(courtyardCentroid, castle.region.width, castle.region.height)
//            .flood8way(castle.courtyard.copy().andNot(castle.keepWall), castle.region.width * castle.region.height);

        Physical carpet = RecipeMixer.buildPhysical(Physical.makeBasic("plush carpet", 'ˬ', SColor.ROYAL_PURPLE));
        Physical insideFloor = getFloor(Stone.GRANITE), insideWall = getWall(Stone.GRANITE);
        DungeonBoneGen dbg = new DungeonBoneGen(rng);
        DungeonGenerator dungeonGenerator = new DungeonGenerator(width, height, rng);
        dungeonGenerator.addDoors(30, true);
        dbg.generate(TilesetType.LIMITED_CONNECTIVITY, width, height);
        GreasedRegion gr = dbg.region.copy();
        dbg.generate(TilesetType.LIMITED_CONNECTIVITY, width + 5, height + 5);
        gr.insert(-5, -5, dbg.region);
        dbg.generate(TilesetType.LIMITED_CONNECTIVITY, width + 3, height + 3);
        gr.not().insert(-2, -2, dbg.region);
        char[][] interior = dungeonGenerator.generate(gr.toChars());
        for (Coord c : castle.insideKeep) {
            EpiTile tile = castle.tileAt(c, castle.ground);
            if(tile == null) {
                castle.setTileAt(c, castle.ground, tile = new EpiTile(insideFloor));
            }
            else {
                tile.floor = insideFloor;
            }
            if(interior[c.x][c.y] == '+' || interior[c.x][c.y] == '/')
            {
                tile.blockage = null;
                placeDoor(tile);
            }
            else if(interior[c.x][c.y] != '.')
                tile.add(insideWall);
        }
//        for (Coord c : castle.insideKeep) {
//            for (int z = 0; z <= 4; z++) {
//                EpiTile tile = castle.tileAt(c, castle.ground - z);
//                if (tile == null) {
//                    castle.setTileAt(c, castle.ground - z, new EpiTile(carpet));
//                } else {
//                    tile.floor = carpet;
//                }
//            }
//        }
//        GreasedRegion doors = castle.keepWall.copy().randomScatter(rng, 8, 5);
//        for (Coord c : doors) {
//            EpiTile tile = castle.tileAt(c, castle.ground);
//            tile.floor = getFloor(Stone.MARBLE);
//            tile.blockage = null;
//            placeDoor(tile);
//        }
    }

    private Coord findCentroid(List<Coord> coords) {
        int centroidX = 0;
        int centroidY = 0;
        for (Coord c : coords) {
            centroidX += c.x;
            centroidY += c.y;
        }
        centroidX /= coords.size();
        centroidY /= coords.size();
        return Coord.get(centroidX, centroidY);
    }

    private List<Coord> findInternalPolygonCorners(GreasedRegion region, int distance, int pointLimit) {
        GreasedRegion points = region.copy();
        do {
            points.remake(region).randomScatter(rng, distance, 12);
            if (points.isEmpty()) {
                System.out.println("No points found for area");
            }
        } while (pointsInLine(points)); // need to make sure at least a triangle is possible

        QuickHull hull = new QuickHull();
        Coord[] coords = points.asCoords();
        return hull.executeQuickHull(coords);// TODO - rework hull to use greased region
    }

    private GreasedRegion connectPoints(GreasedRegion region, Coord... points) {
        Elias elias = new Elias();
        GreasedRegion lines = region.copy();
        for (int i = 0; i < points.length; i++) {
            lines.addAll(elias.line(points[i], points[(i + 1) % points.length]));
        }
        return lines;
    }

    private GreasedRegion connectPoints(GreasedRegion region, List<Coord> points) {
        Elias elias = new Elias();
        GreasedRegion lines = region.copy();
        for (int i = 0; i < points.size(); i++) {
            lines.addAll(elias.line(points.get(i), points.get((i + 1) % points.size())));
        }
        return lines;
    }

    private boolean pointsInLine(GreasedRegion points) {
        int sz = points.size();
        if (sz < 3) {
            return true; // 2 or less points are considered to always be in a line
        }

        double angle = Coord.degrees(points.nth(0), points.nth(1));
        for (int i = 1; i < sz; i++) {
            double test = Coord.degrees(points.nth(i), points.nth((i + 1) % sz));
            if (angle != test && (angle + 180) % 360 != test) {
                return false;
            }
        }

        System.out.println("Points in a line: " + StringKit.join(", ", points));
        return true;
    }

    private boolean pointInBounds(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth;
    }
}
