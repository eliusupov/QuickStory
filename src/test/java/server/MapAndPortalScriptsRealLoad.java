package server;

import client.Character;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;
import scripting.map.MapScriptMethods;
import scripting.portal.PortalPlayerInteraction;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The map-entry and portal hooks added for the maps that declare them in the real {@code wz/} tree
 * but had no script behind them.
 *
 * <p>Both failure modes these cover are SILENT. {@code AbstractScriptManager.getInvocableScriptEngine}
 * returns {@code null} for a missing file without logging, so a missing {@code onUserEnter} hook is
 * invisible; and {@code GenericPortal.enterPortal} only falls back to {@code tm}/{@code tn} when
 * {@code scriptName == null}, so a portal that NAMES an absent script never warps and never logs.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MapAndPortalScriptsRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as {@link EvanChainRealLoad}:
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM and another test class
 * points {@code wz-path} at a {@code @TempDir}.
 */
class MapAndPortalScriptsRealLoad {

    /** onUserEnter hooks added here, and the maps in Map.wz that name each. */
    private static final Map<String, int[]> MAP_HOOKS = new LinkedHashMap<>();
    /** portal hooks added here, and one map that names each. */
    private static final Map<String, Integer> PORTAL_HOOKS = new LinkedHashMap<>();

    static {
        MAP_HOOKS.put("aranTutorAlone",
                new int[]{914000000, 914000300, 914000400, 914000410, 914000420, 914000500});
        MAP_HOOKS.put("go1010400", new int[]{1010400});
        MAP_HOOKS.put("go2000000", new int[]{2000000});
        MAP_HOOKS.put("TD_MC_title", new int[]{106020000});
        MAP_HOOKS.put("TD_NC_title", new int[]{240070000, 240070100, 240070200, 240070300, 240070400,
                240070500, 240070600});
        MAP_HOOKS.put("TD_MC_gasi2", new int[]{106020501});
        MAP_HOOKS.put("undomorphdarco", new int[]{240000110});
        MAP_HOOKS.put("reundodraco", new int[]{270000100});
        MAP_HOOKS.put("evanTogether", new int[]{100030102, 914100021});

        PORTAL_HOOKS.put("hontale_morph", 240040700);
        PORTAL_HOOKS.put("investigate1", 106020300);
        PORTAL_HOOKS.put("tutorWorldmap", 130030006);
        PORTAL_HOOKS.put("piramid_in00", 926010000);
        PORTAL_HOOKS.put("outSDI", 914100022);
        PORTAL_HOOKS.put("enterBlackFrog", 220000300);
    }

    /**
     * {@code scripts/portal/inDragonEgg.js} warps a player who has not started quest 22005 to
     * {@code 100030301} ("Forest Hall"), and that map is deliberately NOT in this tree - its
     * {@code life} places fixed NPCs on 9901910-9901919, the band {@code PlayerNPC.java:66}
     * allocates at runtime. See {@code V84EvanWorldNodeTest.forestHallIsDeliberatelyNotMerged}.
     */
    private static final int FOREST_HALL = 100030301;

    /** The Leafre / Temple of Time flight morph, applied by scripts/npc/2082003.js. */
    private static final int DRAGON_FLIGHT_MORPH = 2210016;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        assertTrue(Files.isDirectory(Path.of(WZFiles.DIRECTORY, "Map.wz", "Map")),
                "wz-path resolved to '" + WZFiles.DIRECTORY + "', which holds no Map.wz - another "
                        + "test class won the WZFiles.DIRECTORY race, so this says nothing");
    }

    /**
     * Every hook added here resolves to a file on the exact path {@code AbstractScriptManager} builds
     * ({@code Path.of("scripts", path)}) and evaluates under the same Graal engine the server uses -
     * which {@code node --check} does not prove, the two parsers are not the same one. The entry
     * points themselves are exercised by the two tests below, which really invoke them.
     */
    @Test
    void everyAddedHookLoadsUnderTheRealEngine() {
        for (String name : MAP_HOOKS.keySet()) {
            assertNotNull(evalOrNull("map/onUserEnter/" + name + ".js"),
                    "scripts/map/onUserEnter/" + name + ".js did not load; "
                            + "MapScriptManager.runMapScript would return false without a log line");
        }
        for (String name : PORTAL_HOOKS.keySet()) {
            assertNotNull(evalOrNull("portal/" + name + ".js"),
                    "scripts/portal/" + name + ".js did not load; the portal stays dead and silent");
        }
    }

    /**
     * Each map hook, run for real against a mocked {@link MapScriptMethods}, does exactly what it is
     * supposed to do and nothing else. This is where the effect paths are pinned: a typo in
     * {@code maplemap/enter/...} or {@code temaD/enter/...} is a silent no-op on the client, so
     * asserting the exact string is the only thing that catches it.
     */
    @Test
    void everyMapHookDoesExactlyWhatItsMapNeeds() throws Exception {
        run("aranTutorAlone", 914000000, ms -> verify(ms).unlockUI());
        run("evanTogether", 100030102, ms -> verify(ms).unlockUI());
        run("TD_MC_gasi2", 106020501, ms -> verify(ms).unlockUI());

        run("go1010400", 1010400, ms -> verify(ms).mapEffect("maplemap/enter/1010400"));
        run("go2000000", 2000000, ms -> verify(ms).mapEffect("maplemap/enter/2000000"));

        run("TD_MC_title", 106020000, ms -> {
            verify(ms).unlockUI();
            verify(ms).mapEffect("temaD/enter/mushCatle");
        });

        run("TD_NC_title", 240070000, ms -> verify(ms).mapEffect("temaD/enter/teraForest"));
        for (int era = 1; era <= 6; era++) {
            final int n = era;
            run("TD_NC_title", 240070000 + era * 100, ms -> verify(ms).mapEffect("temaD/enter/neoCity" + n));
        }

        run("undomorphdarco", 240000110, ms -> verify(ms).cancelItem(DRAGON_FLIGHT_MORPH));
        run("reundodraco", 270000100, ms -> verify(ms).cancelItem(DRAGON_FLIGHT_MORPH));
    }

    /** Each portal hook, run for real against a mocked {@link PortalPlayerInteraction}. */
    @Test
    void everyPortalHookDoesExactlyWhatItsPortalNeeds() throws Exception {
        // The Cave of Life gatekeeper, i.e. the Horntail entrance. Without this the cs00..cs05 row
        // on 240040700 is dead and the only entrance NPC on the map is unreachable by walking.
        runPortal("hontale_morph", pi -> verify(pi).openNpc(2081005));

        runPortal("investigate1", pi -> verify(pi).openNpc(1300014));
        runPortal("piramid_in00", pi -> verify(pi).openNpc(2103013));

        runPortal("tutorWorldmap", pi -> {
            verify(pi).showInfo("UI/tutorial.img/26");
            verify(pi).blockPortal();
        });

        // 914100022's out00 is the only exit of the Cave of Silence ice-wall room and it is
        // scripted, so without this file the room is a one-way trip. The destination is taken from
        // the three sibling rooms, which put out00 on the same pixel as a plain tm/tn portal.
        runPortal("outSDI", pi -> {
            verify(pi).playPortalSound();
            verify(pi).warp(914100010, "in00");
        });
    }

    /**
     * 220000300's {@code scr00} is the only client-side door to the Frog House, and pristine v84
     * gives it two destinations, not one: 922030000 and 922030001 are the ONLY two maps in all of
     * v84 Map.wz whose {@code out00} carries {@code tm=220000300 / tn="scr00"}.
     *
     * <p>The split is quest 22596 ("Rage"), and the quest text picks the map by id:
     * {@code QuestInfo.img/22596/1} says "go to the {@code #b#m922030001##k} in {@code #m220000300#}
     * where you met {@code #p1013203#}", and {@code Check.img/22596/1} wants mob 9300393 x1. Every
     * other visit - {@code Check.img/22581..22588} - names npc 1013203, who stands on 922030000.
     *
     * <p>Run twice against the same file, because one arm of a branch passing proves nothing about
     * the other, and getting the default arm wrong strands every Black Wings mission in an empty
     * room.
     */
    @Test
    void enterBlackFrogPicksTheFrogHouseByQuestState() throws Exception {
        runPortal("enterBlackFrog", pi -> {
            verify(pi).isQuestStarted(22596);
            verify(pi).playPortalSound();
            verify(pi).warp(922030000, 0);
        });

        Invocable iv = evalOrNull("portal/enterBlackFrog.js");
        assertNotNull(iv, "enterBlackFrog.js did not load");
        PortalPlayerInteraction onRage = mock(PortalPlayerInteraction.class);
        when(onRage.isQuestStarted(22596)).thenReturn(true);
        iv.invokeFunction("enter", onRage);
        verify(onRage).isQuestStarted(22596);
        verify(onRage).playPortalSound();
        verify(onRage).warp(922030001, 0);
        verifyNoMoreInteractions(onRage);
    }

    /**
     * A KNOWN-BROKEN warp, pinned so it cannot be quietly "fixed" by inventing a destination.
     *
     * <p>{@code scripts/portal/inDragonEgg.js} is upstream Cosmic, not written here, and its
     * else-branch is what {@code Map.wz} says it should be: {@code 100030300}'s {@code in00}
     * leads to {@code 100030301}, whose own only portal ({@code out00}) leads straight back to
     * {@code 100030300/in00}. But {@code 100030301} is the one map this backport deliberately
     * refused, so today that branch throws inside {@code MapFactory.loadMapFromWz}
     * ({@code mapData} is null), {@code PortalScriptManager.executePortalScript} catches it and
     * logs, and the portal is inert. Every player who has not started quest 22005 - i.e. every
     * non-Evan - can reach {@code 100030300} by walking from Henesys.
     *
     * <p>The fix is to hand-author {@code 100030301} without its ten {@code life} slots, in
     * {@code wz/Map.wz}; the script is already correct and must not be re-pointed. This test
     * fails on the day either half changes, so the two are re-checked together.
     */
    @Test
    void inDragonEggStillWarpsToTheOneMapThisTreeRefusedToMerge() throws IOException {
        assertTrue(bodyOf("portal/inDragonEgg.js").contains("warp(" + FOREST_HALL + ", 0)"),
                "inDragonEgg.js no longer warps to " + FOREST_HALL + " - if " + FOREST_HALL
                        + " was hand-authored, say so here; if the destination was changed, that is "
                        + "invented content, because Map.wz says out00 of " + FOREST_HALL
                        + " returns to 100030300/in00");
        assertFalse(Files.exists(Path.of(WZFiles.DIRECTORY, "Map.wz", "Map", "Map1",
                        FOREST_HALL + ".img.xml")),
                FOREST_HALL + " is now in the tree, so inDragonEgg's else-branch is live again - "
                        + "confirm its life array carries no 9901910-9901919 entry and drop this test");
    }

    /**
     * <strong>The client-crash guard.</strong> {@code showIntro} on a path Effect.wz does not hold
     * takes the client down - that is exactly how every female Evan was crashing on
     * {@code PromiseDragon/Scene1}. None of the names implemented here has a Direction node at all,
     * so none of these scripts may ever grow a {@code showIntro}.
     */
    @Test
    void noneOfTheseNamesIsACutsceneSoNoneOfThemMayCallShowIntro() throws IOException {
        String directions = readAllDirectionImgs();
        Set<String> names = new LinkedHashSet<>(MAP_HOOKS.keySet());
        names.addAll(PORTAL_HOOKS.keySet());

        for (String name : names) {
            assertFalse(directions.contains("<imgdir name=\"" + name + "\">"),
                    "Effect.wz now HAS a Direction node named " + name + " - these scripts were "
                            + "written on the finding that it does not, so revisit them rather than "
                            + "deleting this assertion");
        }
        for (String name : MAP_HOOKS.keySet()) {
            assertFalse(bodyOf("map/onUserEnter/" + name + ".js").contains("showIntro"),
                    name + ".js calls showIntro but has no Direction node; that crashes the client");
        }
        for (String name : PORTAL_HOOKS.keySet()) {
            assertFalse(bodyOf("portal/" + name + ".js").contains("showIntro"),
                    name + ".js calls showIntro but has no Direction node; that crashes the client");
        }
    }

    /**
     * Read straight off Map.wz: every map that names one of these hooks is covered, and the maps
     * listed above really are the ones that name them. Catches both a hook file that drifts out of
     * sync with the data and this test's own map lists going stale.
     */
    @Test
    void theMapsListedHereAreExactlyTheMapsWzDeclaresThemOn() throws IOException {
        Map<String, Set<Integer>> declaredOn = scanMapWzForHooks();

        for (Map.Entry<String, int[]> e : MAP_HOOKS.entrySet()) {
            Set<Integer> expected = new LinkedHashSet<>();
            for (int id : e.getValue()) {
                expected.add(id);
            }
            assertEquals(expected, declaredOn.getOrDefault(e.getKey(), Set.of()),
                    "Map.wz declares onUserEnter=" + e.getKey() + " on a different set of maps than "
                            + "this test claims");
            assertTrue(Files.isRegularFile(Path.of("scripts", "map", "onUserEnter", e.getKey() + ".js")),
                    e.getKey() + " is declared by Map.wz but has no script, so every one of those "
                            + "maps is silent again");
        }
    }

    // ------------------------------------------------------------------ helpers

    private interface Check<T> {
        void check(T t);
    }

    /** Evaluates the hook and invokes {@code start(ms)} on a mock standing on {@code mapId}. */
    private static void run(String name, int mapId, Check<MapScriptMethods> check) throws Exception {
        Invocable iv = evalOrNull("map/onUserEnter/" + name + ".js");
        assertNotNull(iv, name + ".js did not load");

        MapScriptMethods ms = mock(MapScriptMethods.class);
        Character chr = mock(Character.class);
        when(ms.getPlayer()).thenReturn(chr);
        when(ms.getMapId()).thenReturn(mapId);
        when(chr.getMapId()).thenReturn(mapId);

        iv.invokeFunction("start", ms);
        check.check(ms);
    }

    private static void runPortal(String name, Check<PortalPlayerInteraction> check) throws Exception {
        Invocable iv = evalOrNull("portal/" + name + ".js");
        assertNotNull(iv, name + ".js did not load");

        PortalPlayerInteraction pi = mock(PortalPlayerInteraction.class);
        iv.invokeFunction("enter", pi);
        check.check(pi);
        verifyNoMoreInteractions(pi);
    }

    /** Same construction {@code AbstractScriptManager} uses, so a script that loads here loads there. */
    private static Invocable evalOrNull(String path) {
        Path scriptFile = Path.of("scripts", path);
        if (!Files.exists(scriptFile)) {
            return null;
        }
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("graal.js").getFactory()
                .getScriptEngine();
        assertTrue(engine instanceof GraalJSScriptEngine, "no GraalJSScriptEngine on the test classpath");
        Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.put("polyglot.js.allowHostAccess", true);
        bindings.put("polyglot.js.allowHostClassLookup", true);
        try (BufferedReader br = Files.newBufferedReader(scriptFile, StandardCharsets.UTF_8)) {
            engine.eval(br);
        } catch (Exception e) {
            throw new AssertionError("scripts/" + path + " failed to evaluate", e);
        }
        return (Invocable) engine;
    }

    private static String bodyOf(String path) throws IOException {
        return Files.readString(Path.of("scripts", path), StandardCharsets.ISO_8859_1);
    }

    private static String readAllDirectionImgs() throws IOException {
        StringBuilder sb = new StringBuilder();
        Path effect = Path.of(WZFiles.DIRECTORY, "Effect.wz");
        try (Stream<Path> list = Files.list(effect)) {
            for (Path p : list.filter(f -> f.getFileName().toString().startsWith("Direction")).toList()) {
                sb.append(Files.readString(p, StandardCharsets.ISO_8859_1));
            }
        }
        assertFalse(sb.isEmpty(), "no Direction*.img.xml under " + effect);
        return sb.toString();
    }

    /** mapid -> onUserEnter value, for the hook names this test owns. */
    private static Map<String, Set<Integer>> scanMapWzForHooks() throws IOException {
        Pattern hook = Pattern.compile("<string name=\"onUserEnter\" value=\"([^\"]+)\"");
        Map<String, Set<Integer>> out = new LinkedHashMap<>();
        Path maps = Path.of(WZFiles.DIRECTORY, "Map.wz", "Map");
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(maps)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".img.xml"))
                    .forEach(files::add);
        }
        for (Path p : files) {
            String base = p.getFileName().toString().replace(".img.xml", "");
            if (!base.chars().allMatch(java.lang.Character::isDigit)) {
                continue;
            }
            Matcher m = hook.matcher(Files.readString(p, StandardCharsets.ISO_8859_1));
            while (m.find()) {
                if (MAP_HOOKS.containsKey(m.group(1))) {
                    out.computeIfAbsent(m.group(1), k -> new LinkedHashSet<>())
                            .add(Integer.parseInt(base));
                }
            }
        }
        return out;
    }
}
