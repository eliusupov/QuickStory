/*
    This file is part of the Cosmic MapleStory Server, OdinMS-based

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.opcodes;

import constants.net.ServerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Packet opcodes live in {@code resources/opcodes/sendops-NN.properties} and {@code recvops-NN.properties}
 * rather than in compiled literals, so retargeting the server at another client version is a file swap.
 * Pick the table with {@code -Dopcode-version=NN} (default 83). An {@code opcodes/} directory in the
 * working directory overrides the bundled tables, so a version can be tried without rebuilding.
 * <p>
 * Mechanism after Chronicle20/Vertisy's {@code ExternalCodeTableGetter} (same OdinMS/AGPL lineage).
 * <p>
 * Every lookup failure - missing file, missing key, junk value, key that matches no constant - throws,
 * so a broken table stops the server instead of silently becoming opcode 0.
 */
public final class OpcodeTable {
    private static final Logger log = LoggerFactory.getLogger(OpcodeTable.class);
    private static final String VERSION = System.getProperty("opcode-version", "83");
    private static final Properties SEND = load("sendops");
    private static final Properties RECV = load("recvops");

    private OpcodeTable() {
    }

    static int send(String name) {
        return lookup(SEND, "sendops", name);
    }

    static int recv(String name) {
        return lookup(RECV, "recvops", name);
    }

    /**
     * Call once, as early in startup as possible. Touching {@code values()} forces both enums to
     * initialise, which is what surfaces missing/unparseable/out-of-range entries here rather than
     * mid-packet; this method then rejects entries that match no constant, since a typo'd key would
     * otherwise sit in the file doing nothing.
     */
    public static void verify() {
        rejectUnknownKeys(SEND, "sendops", SendOpcode.values());
        rejectUnknownKeys(RECV, "recvops", RecvOpcode.values());

        // launch.bat does not pass -Dopcode-version, so a build that moves ServerConstants.VERSION
        // without it handshakes as one version and speaks the other. Recv ids largely agree between
        // 83 and 84, so packets still arrive and decode - it is the send side that silently lands on
        // the wrong client handler (NPC_TALK 0x130 vs 0x137, SET_FIELD 0x7D vs 0x80). Warn, don't
        // throw: running a mismatched pair on purpose while bisecting is legitimate. Ticket 26.
        if (!VERSION.equals(String.valueOf(ServerConstants.VERSION))) {
            log.warn("Opcode table is v{} but ServerConstants.VERSION is {}. The client will not understand "
                            + "packets whose id moved between the two tables. Start with -Dopcode-version={}.",
                    VERSION, ServerConstants.VERSION, ServerConstants.VERSION);
        }
    }

    private static Properties load(String kind) {
        String path = "opcodes/" + kind + "-" + VERSION + ".properties";
        // An opcodes/ dir in the working directory (same place config.yaml and wz/ are read from) wins over
        // the bundled table, so a version can be tried without rebuilding. The log line below says which won.
        Path onDisk = Path.of(path);
        boolean fromDisk = Files.exists(onDisk);
        try (InputStream in = fromDisk ? Files.newInputStream(onDisk)
                : OpcodeTable.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing opcode table " + path + " (-Dopcode-version=" + VERSION + ")");
            }
            Properties properties = new Properties();
            properties.load(in);
            log.info("Loaded {} {} opcodes from {}", properties.size(), kind,
                    fromDisk ? onDisk.toAbsolutePath() : "classpath:" + path);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read opcode table " + path, e);
        }
    }

    private static int lookup(Properties table, String kind, String name) {
        String value = table.getProperty(name);
        if (value == null) {
            throw new IllegalStateException("Opcode " + name + " has no entry in " + kind + "-" + VERSION + ".properties");
        }
        int code;
        try {
            code = Integer.decode(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Opcode " + name + " has unparseable value '" + value + "' in "
                    + kind + "-" + VERSION + ".properties");
        }
        if (code < 0 || code > 0xFFFF) {   // opcodes go on the wire as an unsigned short
            throw new IllegalStateException("Opcode " + name + " is out of range at " + value + " in "
                    + kind + "-" + VERSION + ".properties");
        }
        return code;
    }

    private static void rejectUnknownKeys(Properties table, String kind, Enum<?>[] opcodes) {
        Set<String> known = Arrays.stream(opcodes).map(Enum::name).collect(Collectors.toSet());
        Set<String> unknown = new TreeSet<>(table.stringPropertyNames());
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Unknown entries in " + kind + "-" + VERSION + ".properties: " + unknown);
        }
    }
}
