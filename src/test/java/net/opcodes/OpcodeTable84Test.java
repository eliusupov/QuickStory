package net.opcodes;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the bundled v84 opcode tables used by the server. The v83 tables remain only as provenance
 * for the collision and unchanged-low-band proofs below; no runtime selector can load them.
 */
class OpcodeTable84Test {

    private static final int UNRESOLVED = 0xFFFF;   // documented placeholder, never on the wire

    private static Properties read(String kind, String version) throws Exception {
        Properties p = new Properties();
        try (InputStream in = OpcodeTable84Test.class.getClassLoader()
                .getResourceAsStream("opcodes/" + kind + "ops-" + version + ".properties")) {
            assertTrue(in != null, kind + "ops-" + version + ".properties is missing");
            p.load(in);
        }
        return p;
    }

    @Test
    void coversEveryOpcodeConstantAndNothingElse() throws Exception {
        assertEquals(names(SendOpcode.values()), new TreeSet<>(read("send", "84").stringPropertyNames()));
        assertEquals(names(RecvOpcode.values()), new TreeSet<>(read("recv", "84").stringPropertyNames()));
    }

    @Test
    void everyValueParsesAndFitsAnUnsignedShort() throws Exception {
        for (String kind : new String[]{"send", "recv"}) {
            Properties p = read(kind, "84");
            for (String name : p.stringPropertyNames()) {
                int code = Integer.decode(p.getProperty(name).trim());
                assertTrue(code >= 0 && code <= 0xFFFF, kind + " " + name + " out of range: " + code);
            }
        }
    }

    /**
     * Two keys may share an opcode only if they already shared one in v83 (Cosmic's WEDDING_TALK /
     * WEDDING_TALK_MORE are both 0x8B). A pair that collides only in v84 means the shift was applied
     * wrongly to one of them - the failure mode that is otherwise silent.
     */
    @Test
    void noOpcodeCollisionThatV83DidNotAlreadyHave() throws Exception {
        for (String kind : new String[]{"send", "recv"}) {
            Map<String, Integer> v83 = decode(read(kind, "83"));
            Map<String, Integer> v84 = decode(read(kind, "84"));
            Map<Integer, List<String>> byCode = new TreeMap<>();
            v84.forEach((name, code) -> {
                if (code != UNRESOLVED) {
                    byCode.computeIfAbsent(code, c -> new ArrayList<>()).add(name);
                }
            });
            byCode.forEach((code, keys) -> {
                Set<Integer> theirV83 = new HashSet<>();
                keys.forEach(k -> theirV83.add(v83.get(k)));
                assertEquals(1, theirV83.size(),
                        kind + "ops-84 collides at 0x" + Integer.toHexString(code) + ": " + keys
                                + " which had distinct v83 opcodes " + theirV83);
            });
        }
    }

    /**
     * Ticket 20 proved the 0x00-0x3E band identical between v83 and v84 by reaching the v84 client's
     * login screen against a v83 table. Anything that moves down there is a bug in the shift, not a
     * discovery.
     */
    @Test
    void lowBandIsByteIdenticalToV83() throws Exception {
        for (String kind : new String[]{"send", "recv"}) {
            Map<String, Integer> v83 = decode(read(kind, "83"));
            Map<String, Integer> v84 = decode(read(kind, "84"));
            v83.forEach((name, code) -> {
                if (code <= 0x3E) {
                    assertEquals(code, v84.get(name),
                            kind + "ops-84 moved " + name + " out of the proven-unchanged 0x00-0x3E band");
                }
            });
        }
    }

    /**
     * Reload the enums in an isolated classloader, read the runtime value off every constant, and
     * compare against the committed v84 file. Asserting the file's own contents alone would not
     * prove what the server loads.
     */
    @Test
    void runtimeTableMatchesTheCommittedV84Files() throws Exception {
        try (URLClassLoader isolated = new URLClassLoader(classpath(), ClassLoader.getPlatformClassLoader())) {
            for (String kind : new String[]{"send", "recv"}) {
                String enumName = "net.opcodes." + (kind.equals("send") ? "Send" : "Recv") + "Opcode";
                Class<?> loaded = Class.forName(enumName, true, isolated);
                assertTrue(loaded.getClassLoader() == isolated, enumName + " was not reloaded in isolation");

                Map<String, Integer> runtime = new TreeMap<>();
                for (Object constant : loaded.getEnumConstants()) {
                    runtime.put(((Enum<?>) constant).name(),
                            (Integer) loaded.getMethod("getValue").invoke(constant));
                }
                assertEquals(decode(read(kind, "84")), runtime,
                        "runtime " + kind + " table differs from " + kind + "ops-84.properties");
            }
        }
    }

    private static URL[] classpath() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        return urls.toArray(new URL[0]);
    }

    private static Map<String, Integer> decode(Properties p) {
        Map<String, Integer> m = new TreeMap<>();
        for (String name : p.stringPropertyNames()) {
            m.put(name, Integer.decode(p.getProperty(name).trim()));
        }
        return m;
    }

    private static Set<String> names(Enum<?>[] values) {
        Set<String> s = new TreeSet<>();
        for (Enum<?> v : values) {
            s.add(v.name());
        }
        return s;
    }
}
