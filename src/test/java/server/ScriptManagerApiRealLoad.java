package server;

import org.junit.jupiter.api.Test;
import scripting.AbstractPlayerInteraction;
import scripting.map.MapScriptMethods;
import scripting.npc.NPCConversationManager;
import scripting.portal.PortalPlayerInteraction;
import scripting.quest.QuestActionManager;
import scripting.reactor.ReactorActionManager;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <strong>Nothing in this server warns when a script names a Java method that does not exist.</strong>
 * Graal resolves {@code cm.whatever(...)} at call time and throws
 * {@code TypeError: Unknown identifier} only once a player walks into that exact branch, so a dead
 * call can sit in a live script for years. That is how ten Evan tutorial scripts came to call
 * {@code qm.sendImage(path)} - no such method on any manager - and every tutorial image in the intro
 * silently failed. The real name was {@link AbstractPlayerInteraction#showInfo(String)}.
 *
 * <p>This test is the load-time warning that does not otherwise exist. It reads the handle each
 * script folder is bound to, reflects the public method names actually on that manager (inherited
 * ones included, which is why this uses {@link Class#getMethods()} rather than parsing sources), and
 * fails on any called name with no match.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=ScriptManagerApiRealLoad
 * </pre>
 *
 * <p>The known-dead calls are in {@link #ALLOWED} with the evidence for each, deliberately as an
 * explicit visible list rather than a silenced pattern. {@link #noAllowlistEntryHasGoneStale} fails
 * if any of them is fixed or deleted without being taken off the list, so the list cannot rot into a
 * blanket exemption.
 *
 * <p><strong>Scope.</strong> This sees a call written literally as {@code handle.name(}. It does not
 * see {@code Java.type} interop, calls on returned objects ({@code cm.getPlayer().foo()}), or a
 * handle passed on to another function. A green run means no script names a method its own manager
 * lacks; it does not mean every script is correct.
 */
class ScriptManagerApiRealLoad {

    /** script folder -> the binding name in that folder, and the class the binding holds. */
    private static final Map<String, Map.Entry<String, Class<?>>> HANDLES = new LinkedHashMap<>();

    static {
        // NPCScriptManager.java:98 / :80 - the same manager, bound as "im" for item scripts.
        HANDLES.put("npc", Map.entry("cm", NPCConversationManager.class));
        HANDLES.put("item", Map.entry("im", NPCConversationManager.class));
        // QuestScriptManager.java:89
        HANDLES.put("quest", Map.entry("qm", QuestActionManager.class));
        // ReactorScriptManager.java:139
        HANDLES.put("reactor", Map.entry("rm", ReactorActionManager.class));
        // pi and ms are not bindings, they are the parameter of enter(pi) / start(ms).
        HANDLES.put("portal", Map.entry("pi", PortalPlayerInteraction.class));
        HANDLES.put("map/onUserEnter", Map.entry("ms", MapScriptMethods.class));
        HANDLES.put("map/onFirstUserEnter", Map.entry("ms", MapScriptMethods.class));
    }

    /**
     * Calls that name a method no manager has and that are being left alone on purpose, each with
     * why it cannot be reached and therefore is not worth inventing a Java method for.
     *
     * <p>Key is the path under {@code scripts/}, value is the method names allowed in that file.
     * A rename to an existing method is always preferred over an entry here; an entry means the
     * script is asking for a capability this server genuinely does not have.
     */
    private static final Map<String, Set<String>> ALLOWED = new LinkedHashMap<>();

    static {
        // Cannoneer (v.111+) and Resistance (v.110+) tutorials. No map in this wz tree names any
        // cannon_tuto_* or Resi_tutor* script, and Npc.wz has no 1096003/1096005, so none of these
        // can run at all. They want the client "direction" (cutscene) API - sendDirectionInfo,
        // setDirectionStatus, lockUI2, startDirection - which this server has never had.
        ALLOWED.put("npc/1096003.js", Set.of("sendDirectionInfo"));
        ALLOWED.put("npc/1096005.js", Set.of("sendDirectionInfo", "removeNPC", "updateInfo"));
        ALLOWED.put("portal/cannon_tuto_06.js", Set.of("setDirectionStatus", "lockUI2"));
        ALLOWED.put("portal/cannon_tuto_07.js", Set.of("setDirectionStatus", "lockUI2",
                "sendDirectionInfo", "updateInfo", "setNPCValue", "spawnNPC"));
        ALLOWED.put("map/onUserEnter/cannon_tuto_01.js", Set.of("setDirectionStatus", "lockUI2",
                "startDirection", "setDirection"));
        ALLOWED.put("map/onUserEnter/cannon_tuto_direction.js", Set.of("setDirectionStatus"));
        ALLOWED.put("map/onUserEnter/cannon_tuto_direction1.js", Set.of("setDirectionStatus",
                "sendDirectionInfo"));
        ALLOWED.put("map/onUserEnter/cannon_tuto_direction2.js", Set.of("setDirectionStatus"));
        ALLOWED.put("map/onUserEnter/Resi_tutor10.js", Set.of("setStandAloneMode"));
        ALLOWED.put("map/onUserEnter/Resi_tutor50.js", Set.of("setDirectionMode"));
        ALLOWED.put("map/onUserEnter/Resi_tutor70.js", Set.of("setDirectionMode"));
        ALLOWED.put("map/onUserEnter/Resi_tutor80.js", Set.of("setDirectionMode"));

        // Quest.wz in this tree has no 23011 and no 2570, so QuestScriptManager never reaches
        // either file. showItemGain is a PacketCreator helper that was never exposed to scripts.
        ALLOWED.put("quest/23011.js", Set.of("showItemGain"));
        ALLOWED.put("quest/2570.js", Set.of("showItemGain"));

        // Kryston, the Pink Bean summon. Npc.wz has 2141000 but NO map in this tree places it, and
        // no script spawns it - PinkBeanBattle.js spawns the boss itself. The script is dead.
        // Implementing removeNpc/forceStartReactor would be building the summon cutscene from
        // scratch for an NPC that is not in the world.
        ALLOWED.put("npc/2141000.js", Set.of("removeNpc", "forceStartReactor"));

        // Mr. Lim. NPC and maps are both in this tree, so this one IS reachable - but the two menu
        // options that throw ask for a subway party quest (start_PyramidSubway / bonus_PyramidSubway)
        // that has no implementation here at all; the only Pyramid this server has is Nett's,
        // via NPCConversationManager.createPyramid. Renaming to createPyramid would send players
        // into different content. Left throwing rather than silently re-pointed. UNRESOLVED.
        ALLOWED.put("npc/1052115.js", Set.of("start_PyramidSubway", "bonus_PyramidSubway"));

        // Ola Ola stage NPC, placed on 109010100. Every path through it first does
        // eim.getPlayers() on cm.getPlayer().getEventInstance(), and no event in scripts/event
        // puts a player into an instance covering 109010100 or 109020001, so the script throws on
        // a null eim well before any of these three. Fixing them would not make it run.
        // mapMobCount would be countMonster() and warpMembers would be eim.warpEventTeam(); clear()
        // has no equivalent and no other script in the tree calls it.
        ALLOWED.put("npc/9000004.js", Set.of("mapMobCount", "clear", "warpMembers"));
    }

    /** {@code handle.name(} - the first segment only, so {@code cm.getPlayer().foo()} is not a hit. */
    private static Pattern callsOn(String handle) {
        return Pattern.compile("\\b" + handle + "\\.(\\w+)\\s*\\(");
    }

    @Test
    void noScriptCallsAMethodItsManagerDoesNotExpose() throws IOException {
        List<String> unknown = new ArrayList<>();

        for (Map.Entry<String, Map.Entry<String, Class<?>>> folder : HANDLES.entrySet()) {
            String handle = folder.getValue().getKey();
            Set<String> available = publicMethodNames(folder.getValue().getValue());
            Pattern call = callsOn(handle);

            for (Path js : scriptsIn(folder.getKey())) {
                String rel = relative(js);
                Set<String> allowed = ALLOWED.getOrDefault(rel, Set.of());
                for (String name : new TreeSet<>(calledNames(call, js))) {
                    if (!available.contains(name) && !allowed.contains(name)) {
                        unknown.add(rel + ": " + handle + "." + name + "() is not on "
                                + folder.getValue().getValue().getSimpleName());
                    }
                }
            }
        }

        if (!unknown.isEmpty()) {
            fail("script(s) call a method the bound manager does not have. Graal throws "
                    + "TypeError: Unknown identifier the moment a player reaches the call, and "
                    + "nothing logs before then. Rename to the real method, or - if this server "
                    + "genuinely lacks the capability - leave it and add it to ALLOWED with the "
                    + "reason it cannot be reached.\n  " + String.join("\n  ", unknown));
        }
    }

    /**
     * Every {@link #ALLOWED} entry still names a real, still-broken call. Without this the list is
     * write-only: a script could be fixed, or deleted, and its exemption would live on and cover
     * the next call that happens to reuse the name.
     */
    @Test
    void noAllowlistEntryHasGoneStale() throws IOException {
        for (Map.Entry<String, Set<String>> e : ALLOWED.entrySet()) {
            Path js = Path.of("scripts", e.getKey());
            assertTrue(Files.isRegularFile(js),
                    "ALLOWED names scripts/" + e.getKey() + ", which no longer exists - drop the entry");

            String handle = handleFor(e.getKey());
            Set<String> called = calledNames(callsOn(handle), js);
            Set<String> stale = new LinkedHashSet<>(e.getValue());
            stale.removeAll(called);
            assertEquals(Set.of(), stale,
                    "scripts/" + e.getKey() + " no longer calls " + stale + " - if it was fixed, "
                            + "take it off ALLOWED so the exemption cannot cover a future call");
        }
    }

    /**
     * The one that would have caught the original bug: {@code sendImage} is gone and may not come
     * back under any handle, in any script folder. Pinned by name because it is the exact spelling
     * that survived for years and is the one a copy-paste from an upstream server reintroduces.
     */
    @Test
    void sendImageIsGoneEverywhere() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String folder : HANDLES.keySet()) {
            for (Path js : scriptsIn(folder)) {
                if (Files.readString(js, StandardCharsets.ISO_8859_1).contains("sendImage")) {
                    offenders.add(relative(js));
                }
            }
        }
        assertEquals(List.of(), offenders,
                "sendImage() is not a method on any manager and never was; the working spelling is "
                        + "showInfo(String), which also sends enableActions()");
    }

    // ------------------------------------------------------------------ helpers

    private static Set<String> publicMethodNames(Class<?> manager) {
        Set<String> names = new HashSet<>();
        for (Method m : manager.getMethods()) {
            names.add(m.getName());
        }
        return names;
    }

    private static Set<String> calledNames(Pattern call, Path js) throws IOException {
        String src = Files.readString(js, StandardCharsets.ISO_8859_1);
        src = src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
        Set<String> out = new LinkedHashSet<>();
        Matcher m = call.matcher(src);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static List<Path> scriptsIn(String folder) throws IOException {
        Path dir = Path.of("scripts", folder.split("/"));
        assertTrue(Files.isDirectory(dir), "no such script folder: " + dir
                + " (tests must run from the repo root, as the script managers do)");
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(p -> p.getFileName().toString().endsWith(".js")).sorted().toList();
        }
    }

    /** {@code npc/1096003.js} -> {@code cm}; the folder is everything before the file name. */
    private static String handleFor(String rel) {
        String folder = rel.substring(0, rel.lastIndexOf('/'));
        Map.Entry<String, Class<?>> h = HANDLES.get(folder);
        if (h == null) {
            throw new IllegalArgumentException("ALLOWED entry in an unscanned folder: " + rel);
        }
        return h.getKey();
    }

    private static String relative(Path js) {
        return Path.of("scripts").relativize(js).toString().replace('\\', '/');
    }
}
