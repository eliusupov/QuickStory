package server;

import provider.DataProvider;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.nio.file.Path;

/**
 * The one way the v84 node tests open the real server XML tree.
 * <p>
 * {@link WZFiles#DIRECTORY} is a {@code static final} resolved once per JVM, and
 * {@code MobSkillFactoryTest} points the {@code wz-path} property at a {@code @TempDir}
 * before it. Whichever test class runs first wins for the entire surefire fork, so anything
 * reading the real tree through {@code WZFiles} / {@code DataProviderFactory} is
 * order-dependent. Constructing {@link XMLWZFile} explicitly is what sidesteps that, and it
 * is the only reason this helper exists.
 * <p>
 * It lived as a verbatim two-line copy in five test classes (ticket 03f, finding F8). The
 * concurrent-flight argument for keeping the test <em>bodies</em> apart never applied to the
 * helper: five copies is five places for the hazard above to be got wrong. Extend the test
 * classes, not this file.
 */
final class V84Wz {

    private V84Wz() {
    }

    /** @param wzFile e.g. {@code "String.wz"} — a directory name under {@code wz/}. */
    static DataProvider wz(String wzFile) {
        return new XMLWZFile(Path.of("wz", wzFile));
    }
}
