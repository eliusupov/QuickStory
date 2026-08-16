package tools.packetvalidator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * <p>This is a test/tooling loader: it reads a repo-relative file, not a classpath resource, so it
 * is not usable from a deployed server.
 */
public final class PacketStructureModels {

    public static final Path DEFAULT_PATH = Path.of("tools", "v84", "decode-models-v84.tsv");

    private PacketStructureModels() {
    }

    public static Map<String, DecodeModel> loadVerified() {
        return loadVerified(DEFAULT_PATH);
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
            List<DecodeModel.Field> fields = new ArrayList<>();
            for (String field : cols[4].split(",")) {
                int sep = field.lastIndexOf(':');
                if (sep < 0) {
                    throw new IllegalStateException("Malformed field '" + field + "' in " + tsv + ": " + line);
                }
                String name = field.substring(0, sep);
                String kind = field.substring(sep + 1);
                fields.add(switch (kind) {
                    case "1" -> new DecodeModel.Field(name, DecodeModel.Kind.U8, 1);
                    case "2" -> new DecodeModel.Field(name, DecodeModel.Kind.U16, 1);
                    case "4" -> new DecodeModel.Field(name, DecodeModel.Kind.U32, 1);
                    case "8" -> new DecodeModel.Field(name, DecodeModel.Kind.U64, 1);
                    case "s" -> new DecodeModel.Field(name, DecodeModel.Kind.STR, 1);
                    default -> {
                        if (!kind.startsWith("b")) {
                            throw new IllegalStateException("Unknown field kind '" + kind + "' in " + line);
                        }
                        yield new DecodeModel.Field(name, DecodeModel.Kind.BUF,
                                Integer.parseInt(kind.substring(1)));
                    }
                });
            }
            out.put(cols[1], new DecodeModel(cols[1], "gms_v83 IDA export " + cols[3]
                    + " (v84 opcode " + cols[2] + "), via tools/v84/derive-decode-models.py",
                    List.copyOf(fields)));
        }
        return out;
    }
}
