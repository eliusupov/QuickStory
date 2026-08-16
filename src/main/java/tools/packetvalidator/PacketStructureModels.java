package tools.packetvalidator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the checked-in decode models produced by {@code tools/v84/derive-decode-models.py}.
 *
 * <p>Only rows the script marked {@code verified} are returned. A {@code candidate} row passed the
 * script's mechanical filters but nobody has confirmed the corresponding {@code PacketCreator}
 * method emits exactly one shape, and a wrong model is worse than no model.
 *
 * <p>This is a test/tooling loader: it reads a working-directory-relative file, not a classpath
 * resource, so it is not usable from a deployed server without pointing
 * {@code -Dpacketvalidator.models} at the file.
 */
public final class PacketStructureModels {

    public static final Path DEFAULT_PATH = Path.of("tools", "v84", "decode-models-v84.tsv");

    private PacketStructureModels() {
    }

    public static Map<String, DecodeModel> loadVerified() {
        return loadVerified(Path.of(System.getProperty("packetvalidator.models", DEFAULT_PATH.toString())));
    }

    public static Map<String, DecodeModel> loadVerified(Path tsv) {
        List<String> lines;
        try {
            lines = Files.readAllLines(tsv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No decode-model table at " + tsv.toAbsolutePath()
                    + " - regenerate it with tools/v84/derive-decode-models.py <atlas-repo>", e);
        }

        Map<String, DecodeModel> out = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length != 5 || !cols[0].equals("verified")) {
                continue;
            }
            DecodeModel.Builder b = DecodeModel.of(cols[1], "gms_v83 IDA export " + cols[3]
                    + " (v84 opcode " + cols[2] + "), via tools/v84/derive-decode-models.py");
            for (String field : cols[4].split(",")) {
                int sep = field.lastIndexOf(':');
                String name = field.substring(0, sep);
                String kind = field.substring(sep + 1);
                switch (kind) {
                    case "1" -> b.u8(name);
                    case "2" -> b.u16(name);
                    case "4" -> b.u32(name);
                    case "8" -> b.u64(name);
                    case "s" -> b.str(name);
                    default -> {
                        if (!kind.startsWith("b")) {
                            throw new IllegalStateException("Unknown field kind '" + kind + "' in " + line);
                        }
                        b.buf(name, Integer.parseInt(kind.substring(1)));
                    }
                }
            }
            out.put(cols[1], b.build());
        }
        return out;
    }
}
