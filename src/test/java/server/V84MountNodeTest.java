package server;

import constants.skills.Beginner;
import constants.skills.Corsair;
import constants.skills.Legend;
import constants.skills.Noblesse;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static server.V84Wz.wz;

/**
 * Ticket 05 — the eight v84 mounts, merged into this repo's server XML tree by
 * {@code docs/wz-baseline/tool-merge}. Path lists:
 * {@code docs/wz-baseline/merge-lists/05/{Character,Skill,Morph,String}.paths.txt}
 * (+8 / +27 / +25 / +7, the last seven all authorised overwrites of {@code MISSING NAME}).
 * <p>
 * A sibling of {@link V84TracerNodeTest} rather than more methods inside it, for the same
 * reason ticket 04's {@link V84CosmeticNodeTest} is: two tickets in flight must not share one
 * file. Same harness, not a second one — the same {@link XMLWZFile} built by hand over an
 * explicit {@code Path.of("wz", …)}, because {@link WZFiles#DIRECTORY} is a
 * {@code static final} resolved once per JVM and {@code MobSkillFactoryTest} points
 * {@code wz-path} at a {@code @TempDir} before it, so whichever test class runs first wins for
 * the whole surefire fork. Never reach the real tree through {@code DataProviderFactory} here.
 */
class V84MountNodeTest {

    // wz(String) lives in V84Wz — one copy for all the v84 node tests (ticket 03f, F8).

    /**
     * The reconciled mount list: {@code {beginner skill id, TamingMob sprite id}}.
     * <p>
     * These are skill-granted mounts, not inventory equips — the sprite is drawn because the
     * server puts that id in the {@code MONSTER_RIDING} buff, never because an item sits in slot
     * -18. So the pairing that has to hold is skill id -> sprite id, and nothing in the WZ states
     * it: it is hardcoded in the client and mirrored in {@link StatEffect}. Corroborated against
     * two independent reimplementations of the same client build —
     * {@code Rebirth95-csharp/src/Rebirth/Characters/Skill/Buff/BuffSkill.cs:310-323} and
     * {@code HaRepacker-src/HaCreator/MapSimulator/Character/Skills/SkillManager.cs:8762-8774}.
     */
    private static final int[][] V84_MOUNTS = {
            {1025, 1932006},    // Charge! Wooden Pony
            {1027, 1932007},    // Croco
            {1028, 1932008},    // Black Scooter
            {1029, 1932009},    // Pink Scooter
            {1030, 1932011},    // Nimbus Cloud
            {1037, 1932018},    // Unicorn
            {1038, 1932019},    // Low Rider
            {1039, 1932020}};   // Red Truck

    /** 1026 is the ninth skill in the same batch: "Soaring", flight, not a mount. */
    private static final int SOARING = 1026;

    /** once per beginner job: Explorer 000.img, Noblesse 1000.img, Legend 2000.img. */
    private static final int[] BEGINNER_JOBS = {0, 1000, 2000};

    @Test
    void v84MountSpritesParse() {
        DataProvider character = wz("Character.wz");
        for (int[] mount : V84_MOUNTS) {
            String img = String.format("TamingMob/0%d.img", mount[1]);
            Data node = character.getData(img);
            assertNotNull(node, "Character.wz/" + img + ".xml did not parse");
            assertEquals("Tm", DataTool.getString("info/islot", node, null), img + " info/islot");
            assertEquals(13, DataTool.getInt("info/reqLevel", node, -1), img + " info/reqLevel");
            assertNotNull(node.getChildByPath("stand1/0"), img + " has no stand1 frames");
        }
    }

    /**
     * {@code info/tamingMob} is a movement class, NOT a mob id. Each mount's value indexes
     * {@code TamingMob.wz/000N.img}, the seven-image speed/jump/fatigue table — the same table
     * v83's own mounts index (Yeti {@code 01932003} is already {@code tamingMob=6}). This test
     * exists because the id sets invited the opposite reading: {@code Mob.wz/8300000}-{@code 07}
     * are real v84 mobs with mount-like names, and they are Crimson Sky ENEMIES (ticket 06),
     * unrelated to these equips.
     */
    @Test
    void tamingMobIndexesTheMovementTableNotAMob() {
        DataProvider tamingMob = wz("TamingMob.wz");
        DataProvider character = wz("Character.wz");
        for (int[] mount : V84_MOUNTS) {
            String img = String.format("TamingMob/0%d.img", mount[1]);
            int idx = DataTool.getInt("info/tamingMob", character.getData(img), -1);
            assertTrue(idx >= 1 && idx <= 7, img + " info/tamingMob=" + idx + " is not a 1-7 index");

            Data cls = tamingMob.getData(String.format("%04d.img", idx));
            assertNotNull(cls, "TamingMob.wz/" + String.format("%04d", idx) + ".img.xml did not parse");
            assertTrue(DataTool.getInt("info/speed", cls, -1) > 0, "movement class " + idx + " has no speed");
        }
    }

    @Test
    void v84MountSkillsParse() {
        for (int jobPrefix : BEGINNER_JOBS) {
            Data bucket = wz("Skill.wz").getData(String.format("%03d.img", jobPrefix));
            assertNotNull(bucket, "Skill.wz/" + jobPrefix + ".img.xml did not parse");
            for (int[] mount : V84_MOUNTS) {
                String id = String.format("%07d", jobPrefix * 10000 + mount[0]);
                Data node = bucket.getChildByPath("skill/" + id);
                assertNotNull(node, "Skill.wz/" + jobPrefix + ".img/skill/" + id + " missing");
                assertEquals(2100000, DataTool.getInt("level/1/time", node, -1), id + " level/1/time");
            }
            String soaring = String.format("%07d", jobPrefix * 10000 + SOARING);
            assertNotNull(bucket.getChildByPath("skill/" + soaring), "Soaring " + soaring + " missing");
        }
    }

    /**
     * Ticket 03f, Edit A. {@code String.wz/Skill.img} is not cosmetic here — it is the
     * ENUMERATION SOURCE the server grants skills from. {@code MaxSkillCommand:44},
     * {@code ResetSkillCommand:44} and {@code NPCConversationManager:395} all iterate
     * {@code getData("Skill.img").getChildren()} and feed each child name to
     * {@code SkillFactory.getSkill(int)}. A skill absent from this image is never visited, so
     * before these 27 rows landed {@code !maxskill} skipped exactly these nine skills and there
     * was no other route to them: this codebase registers no {@code !skill} command
     * (the only skill commands {@code CommandsExecutor} registers anywhere are {@code maxskill}
     * at {@code :417}, {@code resetskill} at {@code :418} and {@code mobskill} at {@code :427}).
     */
    @Test
    void v84MountSkillsAreNamedSoTheServerCanGrantThem() {
        Data skillNames = wz("String.wz").getData("Skill.img");
        assertNotNull(skillNames, "String.wz/Skill.img.xml did not parse");
        for (int jobPrefix : BEGINNER_JOBS) {
            for (int[] mount : V84_MOUNTS) {
                String id = String.format("%07d", jobPrefix * 10000 + mount[0]);
                Data node = skillNames.getChildByPath(id);
                assertNotNull(node, "String.wz/Skill.img/" + id + " missing — !maxskill will skip it");
                assertFalse(DataTool.getString("name", node, "").isBlank(), id + " has no name");
            }
            String soaring = String.format("%07d", jobPrefix * 10000 + SOARING);
            assertEquals("Soaring", DataTool.getString("name", skillNames.getChildByPath(soaring), "").trim(),
                    soaring + " name");
        }
        // This used to be a NEGATIVE control asserting 20011025 was absent, because Evan's copies
        // were held for a later ticket. Ticket 10 is that ticket: it merged Skill.wz/2001.img and
        // the 70 Evan String.wz/Skill.img names, so the row is now legitimately present and the
        // assertion is inverted rather than deleted. See V84EvanNodeTest for the rest.
        assertEquals("Charge! Wooden Pony", DataTool.getString("name", skillNames.getChildByPath("20011025"), "").trim(),
                "20011025 name (imported by ticket 10)");
    }

    /**
     * The wiring the WZ cannot prove: {@link StatEffect} has to recognise every beginner job's copy
     * of a mount skill and none of an advanced job's lookalikes.
     */
    @Test
    void v84MountSkillsMapToTheirSprites() {
        for (int[] mount : V84_MOUNTS) {
            for (int jobPrefix : new int[]{0, 1000, 2000, 2001}) {   // 2001 = Evan
                int skillid = jobPrefix * 10000 + mount[0];
                assertEquals(Integer.valueOf(mount[1]), StatEffect.skillMountItem(skillid),
                        "skill " + skillid + " -> mount sprite");
                assertTrue(StatEffect.isMonsterRidingSkill(skillid),
                        "skill " + skillid + " not recognised as a mount");
            }
        }

        // an advanced job's lookalike must not be read as a mount
        assertNull(StatEffect.skillMountItem(1121017), "Hero 1121017 read as a mount");
        assertFalse(StatEffect.isMonsterRidingSkill(1121017), "Hero 1121017 read as a mount");

        // Soaring is flight, not a mount — it must not take the monster-riding path
        assertNull(StatEffect.skillMountItem(SOARING), "Soaring read as a mount");
        assertFalse(StatEffect.isMonsterRidingSkill(SOARING), "Soaring read as a mount");
    }

    /**
     * The fourth acceptance criterion, as far as a test can carry it: every v83 mount the deleted
     * {@code switch}/if-chain handled must still map to the same sprite.
     * <p>
     * Written against the {@code constants.skills} classes and NOT against literal ids, because a
     * literal is exactly how the first version of this test passed while shipping a regression:
     * the v83 mount ids are <em>not</em> job-stable — Explorer's Yeti Mount 1 is {@code 1017} but
     * Cygnus's is {@code 10001019} and Aran's {@code 20001019}, and Cygnus/Aran's Yeti Mount 2 and
     * Broomstick are {@code x0001022}/{@code x0001023}, which share no suffix with Explorer's
     * {@code 1018}/{@code 1019}. Any scheme keyed on the last four digits gets three of these four
     * families wrong. If someone reintroduces one, this fails.
     */
    @Test
    void v83MountsStillMapToTheSameSprites() {
        assertMount(1932003, Beginner.YETI_MOUNT1, Noblesse.YETI_MOUNT1, Legend.YETI_MOUNT1);
        assertMount(1932004, Beginner.YETI_MOUNT2, Noblesse.YETI_MOUNT2, Legend.YETI_MOUNT2);
        assertMount(1932005, Beginner.WITCH_BROOMSTICK, Noblesse.WITCH_BROOMSTICK, Legend.WITCH_BROOMSTICK);
        assertMount(1932010, Beginner.BALROG_MOUNT, Noblesse.BALROG_MOUNT, Legend.BALROG_MOUNT);

        // these three ride something the table cannot name, so they are recognised without a row:
        // MONSTER_RIDER rides whatever is equipped in slot -18, and SPACESHIP's sprite is
        // 1932000 + skill level. All three must still be monster-riding skills.
        for (int sourceid : new int[]{Beginner.MONSTER_RIDER, Noblesse.MONSTER_RIDER, Legend.MONSTER_RIDER,
                Beginner.SPACESHIP, Noblesse.SPACESHIP, Corsair.BATTLE_SHIP}) {
            assertTrue(StatEffect.isMonsterRidingSkill(sourceid), sourceid + " stopped being a mount skill");
            assertNull(StatEffect.skillMountItem(sourceid), sourceid + " must not have a fixed sprite");
        }
    }

    private static void assertMount(int sprite, int... skillIds) {
        for (int skillid : skillIds) {
            assertEquals(Integer.valueOf(sprite), StatEffect.skillMountItem(skillid),
                    "skill " + skillid + " -> mount sprite");
            assertTrue(StatEffect.isMonsterRidingSkill(skillid),
                    "skill " + skillid + " not recognised as a mount");
        }
    }

    /** v84's flying-mount morph states — the four new images plus fly2 on every existing morph. */
    @Test
    void v84FlyingMorphStatesParse() {
        DataProvider morph = wz("Morph.wz");
        for (int id = 50; id <= 53; id++) {
            Data node = morph.getData(String.format("%04d.img", id));
            assertNotNull(node, "Morph.wz/" + String.format("%04d", id) + ".img.xml did not parse");
            assertTrue(DataTool.getInt("info/speed", node, -1) > 0, id + " has no info/speed");
        }
        for (String img : new String[]{"1000.img", "1001.img", "1002.img", "1003.img",
                "1100.img", "1101.img", "1103.img"}) {
            Data node = morph.getData(img);
            assertNotNull(node.getChildByPath("fly2"), img + " has no fly2");
            assertNotNull(node.getChildByPath("fly2Move"), img + " has no fly2Move");
            assertNotNull(node.getChildByPath("fly2Skill"), img + " has no fly2Skill");
        }
    }

    /**
     * The 7 forced String rows. Every one of these ids already existed locally reading the literal
     * {@code MISSING NAME}, so additive-only could not repair them — they came in through
     * {@code --force COLLISION-FORCE.txt}. Asserting the exact strings is the point: a silent
     * revert to the placeholder is what this test is here to catch.
     */
    @Test
    void forcedEquipNamesReplacedThePlaceholders() {
        Data eqp = wz("String.wz").getData("Eqp.img");
        assertNotNull(eqp, "String.wz/Eqp.img.xml did not parse");

        // Evan's Mir and its saddles. Trailing spaces are v84's own; asserted verbatim.
        assertEquals("Stage 1 Dragon ", name(eqp, "Eqp/Taming/1902040"));
        assertEquals("Stage 2 Dragon", name(eqp, "Eqp/Taming/1902041"));
        assertEquals("Stage 3 Dragon ", name(eqp, "Eqp/Taming/1902042"));
        assertEquals("Stage 1 Dragon Saddle ", name(eqp, "Eqp/Taming/1912033"));
        assertEquals("Stage 2 Dragon Saddle ", name(eqp, "Eqp/Taming/1912034"));
        assertEquals("Stage 3 Dragon Saddle ", name(eqp, "Eqp/Taming/1912035"));

        // the twelve Eqp/Dragon equips, forced as one root
        assertEquals("Silver Mask", name(eqp, "Eqp/Dragon/1942000"));
        assertEquals("Gold Mask", name(eqp, "Eqp/Dragon/1942001"));
        assertEquals("Reverse Mask", name(eqp, "Eqp/Dragon/1942002"));
        assertEquals("Silver Pendant", name(eqp, "Eqp/Dragon/1952000"));
        assertEquals("Gold Pendant", name(eqp, "Eqp/Dragon/1952001"));
        assertEquals("Reverse Pendant", name(eqp, "Eqp/Dragon/1952002"));
        assertEquals("Silver Wings", name(eqp, "Eqp/Dragon/1962000"));
        assertEquals("Gold Wings", name(eqp, "Eqp/Dragon/1962001"));
        assertEquals("Reverse Wings", name(eqp, "Eqp/Dragon/1962002"));
        assertEquals("Silver Tail", name(eqp, "Eqp/Dragon/1972000"));
        assertEquals("Gold Tail", name(eqp, "Eqp/Dragon/1972001"));
        assertEquals("Reverse Tail", name(eqp, "Eqp/Dragon/1972002"));

        // negative control: an id nobody forced still reads the placeholder, so a blanket
        // rewrite of Eqp.img would fail here rather than pass everything above.
        assertEquals("MISSING NAME", name(eqp, "Eqp/Taming/1932000"));
    }

    private static String name(Data eqp, String path) {
        Data node = eqp.getChildByPath(path);
        assertNotNull(node, "String.wz/Eqp.img/" + path + " missing");
        return DataTool.getString("name", node, null);
    }
}
