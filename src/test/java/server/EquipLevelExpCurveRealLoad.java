package server;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataDirectoryEntry;
import provider.DataFileEntry;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the level-up EXP curve of every levelable equip in {@code Character.wz} to the flat
 * {@code 10000} that HeavenMS ships.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EquipLevelExpCurveRealLoad
 * </pre>
 *
 * <p>The flat curve is <strong>not</strong> stock GMS. v83 and v84 stock both ship a real curve
 * ({@code 80,90,100,110,120,0} on the 6-level items, {@code 70,75,80,0} on the 4-level ones) on the
 * 85 equips that stock makes levelable at all. ronancpl's {@code 3a8377c28 "Minor XML patch"}
 * (2018-09-24) rewrote every one of those to {@code 10000} - including the trailing level whose
 * stock value is {@code 0} - and additionally gave a 30-level flat-{@code 10000} curve to 2,876
 * equips that stock never made levelable, for 2,961 in total.
 *
 * <p>That is deliberate server behaviour, and the owner's standing decision is that it also governs
 * anything the v84 backport brings in. This is the one field where the v84-parity policy does not
 * apply, so a v84 merge landing a stock curve here is a regression, not parity. This test is what
 * makes such a merge fail loudly rather than silently re-slowing every equip's level-up.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints {@code wz-path}
 * at a {@code @TempDir}.
 */
class EquipLevelExpCurveRealLoad {

    private static final int HEAVENMS_EXP = 10000;
    private static final int LEVELABLE_EQUIPS = 2961;

    /** Every {@code info/level/info/<n>/exp} in Character.wz, over the whole archive. */
    @Test
    void everyLevelableEquipCarriesTheFlatHeavenMsCurve() {
        DataProvider equipData = DataProviderFactory.getDataProvider(WZFiles.CHARACTER);
        List<String> violations = new ArrayList<>();
        int levelable = 0;
        int expNodes = 0;

        for (DataDirectoryEntry topDir : equipData.getRoot().getSubdirectories()) {
            for (DataFileEntry file : topDir.getFiles()) {
                String path = topDir.getName() + "/" + file.getName();
                Data levelInfo = childByPath(equipData.getData(path), "info/level/info");
                if (levelInfo == null) {
                    continue;
                }
                levelable++;
                for (Data level : levelInfo.getChildren()) {
                    Data exp = level.getChildByPath("exp");
                    if (exp == null) {
                        violations.add(path + " level " + level.getName() + ": no exp node");
                        continue;
                    }
                    expNodes++;
                    int value = DataTool.getInt(exp);
                    if (value != HEAVENMS_EXP) {
                        violations.add(path + " level " + level.getName() + ": exp=" + value);
                    }
                }
            }
        }

        assertEquals(List.of(), violations.size() > 20 ? violations.subList(0, 20) : violations,
                "equips carrying a non-HeavenMS level-up EXP curve (" + violations.size()
                        + " total) - a v84 stock curve was merged in over the flat 10000");
        assertEquals(LEVELABLE_EQUIPS, levelable,
                "the set of levelable equips changed; if that is intended, re-derive this number");
        assertEquals(LEVELABLE_EQUIPS * 30, expNodes, "level-up curves are no longer 30 levels each");
    }

    /**
     * The four Evan dragon-gear equips v84 also edits, plus the cap that ronancpl's diff is quoted
     * from. Named so a failure points at the exact conflict rows rather than a bulk count.
     */
    @Test
    void theV84EditedDragonGearKeepsTheFlatCurve() {
        for (String path : List.of("Dragon/01942002.img", "Dragon/01952002.img",
                "Dragon/01962002.img", "Dragon/01972002.img", "Cap/01002777.img")) {
            Data levelInfo = childByPath(
                    DataProviderFactory.getDataProvider(WZFiles.CHARACTER).getData(path),
                    "info/level/info");
            assertNotNull(levelInfo, path + " lost its info/level node");
            assertEquals(30, levelInfo.getChildren().size(), path + " level count");
            for (Data level : levelInfo.getChildren()) {
                assertEquals(HEAVENMS_EXP, DataTool.getInt(level.getChildByPath("exp")),
                        path + " level " + level.getName() + " exp");
            }
            // stock leaves the last level at 0; ronancpl set it to 10000 like the rest
            assertTrue(levelInfo.getChildByPath("6") != null, path + " has no level 6");
        }
    }

    private static Data childByPath(Data data, String path) {
        return data == null ? null : data.getChildByPath(path);
    }
}
