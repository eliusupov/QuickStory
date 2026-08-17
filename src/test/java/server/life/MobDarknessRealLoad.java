package server.life;

import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import tools.StringUtil;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the v84 {@code elemAttr} Darkness merge through the real decode path.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=MobDarknessRealLoad
 * </pre>
 *
 * <p>v84 did not rebalance mob stats - a three-way audit (v83-stock / v84-stock / this tree, see
 * {@code tools/parity/reports/rebalance-mob-*.txt}) found zero movement in maxHP, exp, level,
 * PADamage, PDDamage, MADamage, MDDamage, acc, eva, pushed and speed across 1,564 shared mobs. The
 * one stat that moved and that the server reads is {@code elemAttr}: v84 appends a Darkness term to
 * exactly 160 mobs, every one of them exactly {@code v84 == v83 + "D<n>"}. Those 160 rows were
 * merged with {@code WzMerge xml --force docs/wz-baseline/merge-lists/34/Mob.elemAttr.force.txt}.
 *
 * <p>Nothing in {@code src/main} changed for it: {@link Element#getFromChar(char)} already maps
 * 'D' to {@link Element#DARKNESS} and {@code LifeFactory.decodeElementalString} already splits the
 * string two characters at a time. This test is the proof that the data actually reaches
 * {@link MonsterStats#getEffectiveness(Element)}, because with no D term that getter silently
 * returns {@link ElementalEffectiveness#NORMAL} and an Evan dark attack is scaled as if the mob had
 * no opinion about darkness at all. 141 of the 160 gain D1/D2 (IMMUNE/STRONG - Evan was hitting
 * them harder than v84 intends); the other 19 gain D3 (WEAK - he was hitting them too softly).
 *
 * <p><strong>Why reflection and not {@code LifeFactory.getMonster}.</strong> {@code getMonster}
 * touches {@code MonsterInformationProvider.getInstance()}, whose static initialiser reads the
 * {@code monstercarddata} table and throws without a connection pool. {@code V84TracerNodeTest}
 * already records why the suite will not take that dependency: on a machine without a database it
 * burns the 90 s {@code INIT_CONNECTION_POOL_TIMEOUT}, and on a machine with one it leaves a static
 * dataSource set for the rest of the surefire fork. So this reads the node the way
 * {@code LifeFactory.getMonsterStats} reads it (same provider, same 11-char left pad, same
 * {@code DataTool.getString}) and then calls the real, unmodified
 * {@code decodeElementalString(MonsterStats, String)}. Everything under test is production code:
 * the two-character split, {@link Element#getFromChar(char)},
 * {@link ElementalEffectiveness#getByNumber(int)} and the {@link MonsterStats} map.
 *
 * <p>Expected values are transcribed from {@code WzMerge dump D:\games\wz-stage\v84-base\Mob.wz},
 * path {@code Mob.wz/<id>.img/info/elemAttr}, on the hash-verified v84 stock tree. Each case
 * asserts the WHOLE resistance map, not just the Darkness entry, so a merge that damaged one of the
 * pre-existing terms fails here too.
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>: {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} repoints
 * {@code wz-path} at a {@code @TempDir}.
 */
class MobDarknessRealLoad {

    /** D1 -> IMMUNE. 9300086 is one of only three mobs v84 gives a D1. */
    @Test
    void mob9300086IsImmuneToDarkness() {
        assertEquals(map(Element.FIRE, ElementalEffectiveness.WEAK,
                        Element.ICE, ElementalEffectiveness.IMMUNE,
                        Element.LIGHTING, ElementalEffectiveness.IMMUNE,
                        Element.POISON, ElementalEffectiveness.IMMUNE,
                        Element.HOLY, ElementalEffectiveness.IMMUNE,
                        Element.NEUTRAL, ElementalEffectiveness.IMMUNE,
                        Element.DARKNESS, ElementalEffectiveness.IMMUNE),
                resistance(9300086),
                "9300086 elemAttr should be v84's F3I1L1S1H1P1D1");
    }

    /** D2 -> STRONG, the overwhelming majority: 138 of the 160. */
    @Test
    void mob2230101ResistsDarkness() {
        assertEquals(map(Element.HOLY, ElementalEffectiveness.WEAK,
                        Element.DARKNESS, ElementalEffectiveness.STRONG),
                resistance(2230101),
                "2230101 elemAttr should be v84's H3D2");
    }

    /** A compound string, to prove the D term appended and did not replace. */
    @Test
    void mob4240000KeepsItsFourOldTermsAndGainsDarkness() {
        assertEquals(map(Element.ICE, ElementalEffectiveness.STRONG,
                        Element.LIGHTING, ElementalEffectiveness.STRONG,
                        Element.FIRE, ElementalEffectiveness.STRONG,
                        Element.HOLY, ElementalEffectiveness.STRONG,
                        Element.DARKNESS, ElementalEffectiveness.STRONG),
                resistance(4240000),
                "4240000 elemAttr should be v84's I2L2F2H2D2");
    }

    /** D3 -> WEAK, 19 of the 160. These are the mobs Evan's dark attacks should hit HARDER. */
    @Test
    void mob3000000IsWeakToDarkness() {
        assertEquals(map(Element.HOLY, ElementalEffectiveness.STRONG,
                        Element.LIGHTING, ElementalEffectiveness.STRONG,
                        Element.ICE, ElementalEffectiveness.WEAK,
                        Element.DARKNESS, ElementalEffectiveness.WEAK),
                resistance(3000000),
                "3000000 elemAttr should be v84's H2L2I3D3");
    }

    /**
     * The control. 8510000 (Papulatus Clock) carries an elemAttr but is NOT one of the 160, so the
     * merge must have left it alone and DARKNESS must still fall through to NORMAL. This is what
     * fails if someone ever "fixes" darkness by writing a D term everywhere.
     */
    @Test
    void mob8510000OutsideTheMergeHasNoDarknessTerm() {
        assertEquals(map(Element.FIRE, ElementalEffectiveness.WEAK), resistance(8510000),
                "8510000 is not in the 160-mob v84 Darkness set and must be untouched");
        assertEquals(ElementalEffectiveness.NORMAL, statsOf(8510000).getEffectiveness(Element.DARKNESS),
                "an absent element must read as NORMAL, not as a resistance");
    }

    private static Map<Element, ElementalEffectiveness> resistance(int mobId) {
        MonsterStats stats = statsOf(mobId);
        Map<Element, ElementalEffectiveness> read = new EnumMap<>(Element.class);
        for (Element e : Element.values()) {
            ElementalEffectiveness ee = stats.getEffectiveness(e);
            if (ee != ElementalEffectiveness.NORMAL) {
                read.put(e, ee);   // read back through the real getter, not the raw map
            }
        }
        return read;
    }

    /** The {@code elemAttr} half of {@code LifeFactory.getMonsterStats}, minus its DB-bound half. */
    private static MonsterStats statsOf(int mobId) {
        DataProvider mob = DataProviderFactory.getDataProvider(WZFiles.MOB);
        Data monsterData = mob.getData(StringUtil.getLeftPaddedStr(mobId + ".img", '0', 11));
        assertNotNull(monsterData, "wz/Mob.wz has no image for mob " + mobId);
        Data info = monsterData.getChildByPath("info");
        assertNotNull(info, "mob " + mobId + " has no info node");

        MonsterStats stats = new MonsterStats();
        try {
            Method decode = LifeFactory.class.getDeclaredMethod("decodeElementalString",
                    MonsterStats.class, String.class);
            decode.setAccessible(true);
            decode.invoke(null, stats, DataTool.getString("elemAttr", info, ""));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("LifeFactory.decodeElementalString(MonsterStats, String) is gone "
                    + "or changed shape - this test pins the elemAttr decode and must be re-pointed", e);
        }
        return stats;
    }

    private static Map<Element, ElementalEffectiveness> map(Object... kv) {
        Map<Element, ElementalEffectiveness> m = new EnumMap<>(Element.class);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((Element) kv[i], (ElementalEffectiveness) kv[i + 1]);
        }
        return m;
    }
}
