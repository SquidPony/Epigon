package squidpony.epigon.playground.tests;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.TimeUtils;

public class MapMemoryTest extends ApplicationAdapter {
    private static final int width = 5000, height = 5000;
    // the initial bug was reported on ObjectMap
//    private com.badlogic.gdx.utils.ObjectMap<GridPoint2, Integer> theMap;
    private ObjectMap<Vector2, Integer> theMap;
//    private com.badlogic.gdx.utils.ObjectMap<Vector2, Integer> theMap;
//    private com.badlogic.gdx.utils.ObjectMap<com.badlogic.gdx.math.Vector2, Integer> theMap;
//    private HashMap<com.badlogic.gdx.math.GridPoint2, Integer> theMap;

    // HashSet from the JDK
    //200x200
    //Initial allocated space for Set: Not supported
    //21198700ns taken, about 10 to the 7.326309228846793 power.
    //Post-assign allocated space for Set: Not supported    
    //1000x1000
    //Initial allocated space for Set: Not supported
    //913632622ns taken, about 10 to the 8.960771598018294 power.
    //Post-assign allocated space for Set: Not supported
    //1000x1000 Strings
    //Initial allocated space for Set: Not supported
    //893019736ns taken, about 10 to the 8.950861057030966 power.
    //Post-assign allocated space for Set: Not supported
//    private HashSet<Object> theMap;
    // Linear probing UnorderedSet, from SquidLib
    //200x200
    //Initial allocated space for Set: 2049
    //2383177240ns taken, about 10 to the 9.377156342594699 power.
    //Post-assign allocated space for Set: 131073
    //1000x1000
    //Initial allocated space for Set: 2049
    //43338435400ns taken, about 10 to the 10.636873228409462 power.
    //Post-assign allocated space for Set: 2097153
    //1000x1000 Strings
    //Initial allocated space for Set: 2049
    //868829554ns taken, about 10 to the 8.938934585404091 power.
    //Post-assign allocated space for Set: 2097153
//    private UnorderedSet<Object> theMap;
    // DoubleHashing mostly as-is, some adjustments to use a mask
    //200x200
    //Initial allocated space for Set: 2048
    //33255870ns taken, about 10 to the 7.521868313807727 power.
    //Post-assign allocated space for Set: 131072
    //1000x1000
    //Initial allocated space for Set: 2048
    //791335494ns taken, about 10 to the 8.898360645700507 power.
    //Post-assign allocated space for Set: 2097152
    // DoubleHashing with no modulus used
    //200x200
    //Initial allocated space for Set: 2048
    //21927116ns taken, about 10 to the 7.340981514137573 power.
    //Post-assign allocated space for Set: 131072 
    //1000x1000
    //Initial allocated space for Set: 2048
    //719916826ns taken, about 10 to the 8.857282324075996 power.
    //Post-assign allocated space for Set: 2097152
    //1000x1000 Strings
    //Initial allocated space for Set: 2048
    //895451080ns taken, about 10 to the 8.952041864594444 power.
    //Post-assign allocated space for Set: 2097152
//    private DoubleHashing<Object> theMap;
    // RobinHood WITHOUT better hash mixing:
    //200x200
    //Initial allocated space for Set: 2048
    //9836116046ns taken, about 10 to the 9.992823643881254 power.
    //Post-assign allocated space for Set: 131072
    //1000x1000
    //Initial allocated space for Set: 2048
    //133757218480ns taken, about 10 to the 11.126317228909627 power.
    //Post-assign allocated space for Set: 2097152
    
    // RobinHood WITH better hash mixing:
    //200x200
    //Initial allocated space for Set: 2048
    //34606440ns taken, about 10 to the 7.539156925272842 power.
    //Post-assign allocated space for Set: 131072
    //1000x1000
    //Initial allocated space for Set: 2048
    //953714426ns taken, about 10 to the 8.97941835187509 power.
    //Post-assign allocated space for Set: 2097152
    //1000x1000 Strings
    //Initial allocated space for Set: 2048
    //990291616ns taken, about 10 to the 8.995763102244613 power.
    //Post-assign allocated space for Set: 2097152
    //private RobinHood<Object> theMap;
    
//    private ObjectSet<Object> theMap;
    @Override
    public void create() {
//        theMap = new UnorderedSet<>(1024, 0.5f);
//        theMap = new DoubleHashing<>(2048);
//        theMap = new RobinHood<>(2048);
//        theMap = new HashSet<>(2048, 0.5f);
//        theMap = new ObjectSet<>(2048, 0.5f);
//        theMap = new HashMap<>(1000000, 0.5f);
//        theMap = new ObjectMap<>(1000000, 0.5f);
        theMap = new ObjectMap<>(1000000, 0.5f);
        generate();
    }

    private static long szudzik(long x, long y) {
        x = (x << 1) ^ (x >> 63);
        y = (y << 1) ^ (y >> 63);
        return (x >= y ? x * x + x + y : x + y * y);
    }

    private static void unSzudzik(long[] output, long z) {
        final long low = (long)Math.sqrt(z), lessSquare = z - low * low, x, y;
        if(lessSquare < low) { 
            x = lessSquare;
            y = low;
        }
        else {
            x = low;
            y = lessSquare - low;
        }
        output[0] = x >> 1 ^ -(x & 1L);
        output[1] = y >> 1 ^ -(y & 1L);
    }


    public void generate()
    {
//        long[] pair = new long[2];
//        System.out.println("Initial allocated space for Set: Not supported");
        System.out.println("Initial allocated space for Set: " + theMap.capacity);
        final long startTime = TimeUtils.nanoTime();
//        Mnemonic m = new Mnemonic(123456789L);
        Vector2 gp = new Vector2(0, 0);
//        GridPoint2 gp = new GridPoint2(0, 0);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
//                for (int z = -height; z < height; z++) {

//                long z = (x & 0xFFFFFFFFL) << 32 | (y & 0xFFFFFFFFL);
//                z =        ((z & 0x00000000ffff0000L) << 16) | ((z >>> 16) & 0x00000000ffff0000L) | (z & 0xffff00000000ffffL);
//                z =        ((z & 0x0000ff000000ff00L) << 8 ) | ((z >>> 8 ) & 0x0000ff000000ff00L) | (z & 0xff0000ffff0000ffL);
//                z =        ((z & 0x00f000f000f000f0L) << 4 ) | ((z >>> 4 ) & 0x00f000f000f000f0L) | (z & 0xf00ff00ff00ff00fL);
//                z =        ((z & 0x0c0c0c0c0c0c0c0cL) << 2 ) | ((z >>> 2 ) & 0x0c0c0c0c0c0c0c0cL) | (z & 0xc3c3c3c3c3c3c3c3L);
//                z =        ((z & 0x2222222222222222L) << 1 ) | ((z >>> 1 ) & 0x2222222222222222L) | (z & 0x9999999999999999L);
//                theMap.put(z, null);                                                 // uses 23312536 bytes of heap
//                long z = szudzik(x, y);
//                theMap.put(z, null);                                                   // uses 18331216 bytes of heap?
//                unSzudzik(pair, z);
//                theMap.put(0xC13FA9A902A6328FL * x ^ 0x91E10DA5C79E7B1DL * y, null); // uses 23312576 bytes of heap
//                theMap.put((x & 0xFFFFFFFFL) << 32 | (y & 0xFFFFFFFFL), null);       // uses 28555456 bytes of heap
//                theMap.add(new Vector2(x - width * 0.5f, y - height * 0.5f));
//                theMap.put(new Vector2((x * 0xC13FA9A9 >> 8), (y * 0x91E10DA5 >> 8)), x); // sub-random point sequence
                theMap.put(new Vector2(x - 2500, y - 2500), x);
//                theMap.put(new com.badlogic.gdx.math.Vector2(x - 25, y - 25), x);  // crashes out of heap with 50x50 Vector2

//                theMap.put(new GridPoint2(x - 1500, y - 1500), x); // crashes out of heap with 720 Vector2
//                    gp.set(x, y);
//                    theMap.add(gp.hashCode());
//                    long r, s;
//                    r = (x ^ 0xa0761d65L) * (y ^ 0x8ebc6af1L);
//                    s = 0xa0761d65L * (z ^ 0x589965cdL);
//                    r -= r >> 32;
//                    s -= s >> 32;
//                    r = ((r ^ s) + 0xeb44accbL) * 0xeb44acc8L;
//                    theMap.add((int)(r - (r >> 32)));

//                theMap.add(m.toMnemonic(szudzik(x, y)));
                }
            }
//        }
//                final GridPoint2 gp = new GridPoint2(x, y);
//                final int gpHash = gp.hashCode(); // uses the updated GridPoint2 hashCode(), not the current GDX code
//                theMap.put(gp, gpHash | 0xFF000000); //value doesn't matter; this was supposed to test ObjectMap
                //theMap.put(gp, (53 * 53 + x + 53 * y) | 0xFF000000); //this is what the hashCodes would look like for the current code
                
                //final int gpHash = x * 0xC13F + y * 0x91E1; // updated hashCode()
                //// In the updated hashCode(), numbers are based on the plastic constant, which is
                //// like the golden ratio but with better properties for 2D spaces. These don't need to be prime.
                
                //final int gpHash = 53 * 53 + x + 53 * y; // equivalent to current hashCode()
        long taken = TimeUtils.timeSinceNanos(startTime);
        System.out.println(taken + "ns taken, about 10 to the " + Math.log10(taken) + " power.");
        System.out.println("Post-assign allocated space for Set: " + theMap.capacity);
//        System.out.println("Post-assign allocated space for Set: Not supported");
    }

    @Override
    public void render() {
        // standard clear the background routine for libGDX
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }


    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("LibGDX Test: ObjectMap<GridPoint2, Integer> memory usage");
        config.setWindowedMode(500, 100);
        config.setIdleFPS(1);
        new Lwjgl3Application(new MapMemoryTest(), config);
    }
}