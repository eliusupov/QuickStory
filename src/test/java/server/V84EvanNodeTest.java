package server;

import client.Character;
import client.Client;
import client.Job;
import constants.game.GameConstants;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import provider.DataTool;
import provider.wz.WZFiles;
import provider.wz.XMLWZFile;
import server.maps.AbstractMapObject;
import server.maps.Dragon;
import server.maps.MapObjectType;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static server.V84Wz.wz;

/**
 * Ticket 10 — Evan exists, renders, and has a dragon.
 * <p>
 * Path lists: {@code docs/wz-baseline/merge-lists/10/{Skill,String,Etc,UI}.paths.txt}
 * (+12 / +70 / +4 / +4, {@code added 90, refused 0, denied 0, forced 0} on the binary side).
 * <p>
 * A sibling of {@link V84TracerNodeTest} for the same reason {@link V84MountNodeTest} is, and it
 * opens the tree the same single way — {@link V84Wz#wz} over an explicit {@code Path.of("wz", …)},
 * never {@code DataProviderFactory}, because {@link WZFiles#DIRECTORY} is a {@code static final}
 * resolved once per JVM and {@code MobSkillFactoryTest} redirects {@code wz-path} at a
 * {@code @TempDir} before it. See {@link XMLWZFile}.
 */
class V84EvanNodeTest {

    /** EVAN1..EVAN10. There is no 2219 — the tenth level is 2218. */
    private static final int[] EVAN_JOB_IMAGES = {2200, 2210, 2211, 2212, 2213, 2214, 2215, 2216, 2217, 2218};

    /**
     * The eight ids {@link StatEffect#buildSkillMounts} mints for job 2001, paired with the name
     * that table asserts they carry, taken from the merged {@code String.wz/Skill.img}.
     * <p>
     * This is the resolution of hazard F4 (recorded by 03f, and by 03g in a comment inside
     * {@code StatEffect}): the 2001 rows of that table were <em>speculative</em>, derived from
     * add-list naming while {@code Skill.wz/2001.img} was unmerged, and the warning was that if
     * 2001.img turned out to ship a real Evan skill at one of those ids it would silently become
     * a mount. Ticket 10 merged that image and dumped it. The speculation is <b>confirmed, 8/8 by
     * name</b> — every one of these ids is the very mount the table claims — so the mapping stands
     * and nothing had to be dropped from the loop.
     */
    private static final Object[][] EVAN_MOUNT_NAMES = {
            {20011025, "Charge! Wooden Pony", 1932006},
            {20011027, "Croco", 1932007},
            {20011028, "Black Scooter", 1932008},
            {20011029, "Pink Scooter", 1932009},
            {20011030, "Nimbus Cloud", 1932011},
            {20011037, "Unicorn", 1932018},
            {20011038, "Low Rider", 1932019},
            {20011039, "Red Truck", 1932020}};

    /**
     * Every Evan job image is present in the server XML tree and carries its skills.
     * <p>
     * {@code SkillFactory} resolves a skill as {@code Skill.wz/<jobid>.img/skill/<id>}, so a
     * missing image is the difference between {@code getSkill(22171000)} returning a skill and
     * returning {@code null} — and {@code Character.setMasteries(2217)} feeds that straight into
     * {@code changeSkillLevel}, which dereferences it. Before this merge those images did not
     * exist at all: {@code wz/Skill.wz/} stopped at Aran's {@code 2112.img.xml}.
     */
    @Test
    void everyEvanJobImageIsInTheServerTree() {
        DataProvider skills = wz("Skill.wz");

        Data beginner = skills.getData("2001.img");
        assertNotNull(beginner, "Skill.wz/2001.img.xml did not parse — job 2001 has no skills");
        assertNotNull(beginner.getChildByPath("skill/20011000"), "2001.img/skill/20011000");

        for (int job : EVAN_JOB_IMAGES) {
            Data img = skills.getData(job + ".img");
            assertNotNull(img, "Skill.wz/" + job + ".img.xml missing");
            Data skillNode = img.getChildByPath("skill");
            assertNotNull(skillNode, job + ".img has no skill node");
            assertFalse(skillNode.getChildren().isEmpty(), job + ".img/skill is empty");
        }
    }

    /**
     * The four ids {@code Character.setMasteries} hands to {@code SkillFactory.getSkill} for jobs
     * 2217 and 2218. A null there reaches {@code changeSkillLevel(skill, …)} and NPEs on
     * {@code skill.getId()} — so this is the one place the missing WZ was not merely inert.
     */
    @Test
    void setMasteriesSkillsForEvan9And10Resolve() {
        DataProvider skills = wz("Skill.wz");
        assertNotNull(skills.getData("2217.img").getChildByPath("skill/22171000"), "Evan.MAPLE_WARRIOR");
        assertNotNull(skills.getData("2217.img").getChildByPath("skill/22171002"), "Evan.ILLUSION");
        assertNotNull(skills.getData("2218.img").getChildByPath("skill/22181000"), "Evan.BLESSING_OF_THE_ONYX");
        assertNotNull(skills.getData("2218.img").getChildByPath("skill/22181001"), "Evan.BLAZE");
    }

    /**
     * Hazard F4, settled with data rather than with an argument. See {@link #EVAN_MOUNT_NAMES}.
     */
    @Test
    void evansEightMountIdsAreTheMountsStatEffectSaysTheyAre() {
        Data skillNames = wz("String.wz").getData("Skill.img");
        for (Object[] row : EVAN_MOUNT_NAMES) {
            int id = (Integer) row[0];
            Data node = skillNames.getChildByPath(String.valueOf(id));
            assertNotNull(node, "String.wz/Skill.img/" + id + " missing");
            assertEquals(row[1], DataTool.getString("name", node, "").trim(), id + " name");
            assertEquals(row[2], StatEffect.skillMountItem(id), id + " -> mount sprite");
        }
    }

    /**
     * Soaring is the id inside that range that is <em>not</em> a mount, and the merged data says
     * so in its own words: it is flight, and ticket 05 already excluded {@code 1026} for the
     * beginner jobs on exactly that reasoning. This is the negative control for the test above —
     * if someone ever widens {@code buildSkillMounts} from an explicit list back to a
     * {@code 20011025..20011039} range, this fails.
     */
    @Test
    void evansSoaringIsFlightNotAMount() {
        Data soaring = wz("String.wz").getData("Skill.img").getChildByPath("20011026");
        assertNotNull(soaring, "String.wz/Skill.img/20011026 missing");
        assertEquals("Soaring", DataTool.getString("name", soaring, "").trim());
        assertNull(StatEffect.skillMountItem(20011026), "Soaring read as a mount");
        assertFalse(StatEffect.isMonsterRidingSkill(20011026), "Soaring read as a mount");
    }

    /**
     * A stated gap, pinned so it cannot be forgotten or quietly "fixed" by guesswork.
     * <p>
     * {@code Skill.wz/2001.img} also ships {@code 20011018} "Yeti Rider", {@code 20011019}
     * "Witch's Broomstick" and {@code 20011031} "Balrog" — real v83-era mounts — and none of them
     * is in {@code SKILL_MOUNTS}, because {@code constants.skills.Evan} declares no
     * {@code YETI_MOUNT}/{@code WITCH_BROOMSTICK}/{@code BALROG_MOUNT}. The id offsets do not
     * transfer: Beginner numbers them 1017/1018/1019 and Legend/Noblesse 1019/1022/1023, and
     * Evan's 1018 is named "Yeti Rider" rather than "Yeti Mount 2", so which sprite each one wants
     * cannot be derived — it would be the same speculation F4 warned about. Ticket 12 owns it.
     * Cost today: an Evan given one of the three by {@code !maxskill} casts it and gets no mount.
     */
    @Test
    void evansThreeV83EraMountsAreNamedButUnmapped() {
        Data skillNames = wz("String.wz").getData("Skill.img");
        assertEquals("Yeti Rider", DataTool.getString("name", skillNames.getChildByPath("20011018"), "").trim());
        assertEquals("Witch's Broomstick", DataTool.getString("name", skillNames.getChildByPath("20011019"), "").trim());
        assertEquals("Balrog", DataTool.getString("name", skillNames.getChildByPath("20011031"), "").trim());
        for (int id : new int[]{20011018, 20011019, 20011031}) {
            assertNull(StatEffect.skillMountItem(id), id + " unexpectedly mapped — update the doc on this test");
        }
    }

    /**
     * FIXED 2026-08-16 — this test used to assert the bug and has been inverted to lock in the fix.
     * <p>
     * {@code isMonsterRidingSkill} tested {@code sourceid % 10000000 == 1004}. That arithmetic
     * works for Beginner (1004), Noblesse (10001004) and Legend (20001004), whose job prefixes are
     * four digits — but Evan's job block is 2001, so {@code 20011004 % 10000000} is 11004 and the
     * test failed. The predicate now enumerates the four {@code MONSTER_RIDER} constants.
     * <p>
     * Widening to {@code % 10000 == 1004} would also have "worked" and is why the enumeration was
     * chosen instead: it would match Power Strike (1001004) and every other first-job skill ending
     * in 1004. The final assertion pins that, so a future simplification back to modulo fails here.
     */
    @Test
    void evansMonsterRiderIsRecognised() {
        assertEquals(20011004, constants.skills.Evan.MONSTER_RIDER);
        assertTrue(StatEffect.isMonsterRidingSkill(1004), "Beginner MONSTER_RIDER");
        assertTrue(StatEffect.isMonsterRidingSkill(10001004), "Noblesse MONSTER_RIDER");
        assertTrue(StatEffect.isMonsterRidingSkill(20001004), "Legend MONSTER_RIDER");
        assertTrue(StatEffect.isMonsterRidingSkill(20011004), "Evan MONSTER_RIDER");
        assertFalse(StatEffect.isMonsterRidingSkill(1001004),
                "Power Strike must not be a mount — the predicate has been widened to % 10000");
    }

    /**
     * The 70 Evan name rows land where {@code !maxskill} can see them.
     * <p>
     * {@code MaxSkillCommand:44} iterates the children of {@code String.wz/Skill.img} — it is the
     * enumeration source for granting skills, not {@code Skill.wz} — so a skill absent from this
     * image is never visited at all. That is the same finding review Edit A made about the mount
     * skills; it applies identically here.
     */
    @Test
    void everyEvanSkillNameIsInStringImgSoMaxskillVisitsIt() {
        Data skillNames = wz("String.wz").getData("Skill.img");
        List<String> expected = List.of(
                "2001", "2200", "2210", "2211", "2212", "2213", "2214", "2215", "2216", "2217", "2218",
                "20010012", "20011000", "20011011", "20019002",
                "22000000", "22001001", "22101000", "22111001", "22121000", "22131001",
                "22141003", "22151003", "22161003", "22171004", "22181003");
        for (String id : expected) {
            assertNotNull(skillNames.getChildByPath(id), "String.wz/Skill.img/" + id + " missing");
        }
        assertEquals(70, wzRowsAddedToSkillImg(skillNames), "Evan rows under String.wz/Skill.img");
    }

    /** Counts the ids this ticket added: every 2001/22xx job label and every 2001xxxx/22xxxxxx id. */
    private static int wzRowsAddedToSkillImg(Data skillNames) {
        return (int) skillNames.getChildren().stream()
                .map(Data::getName)
                .filter(n -> n.equals("2001") || n.startsWith("2001") && n.length() == 8
                        || n.equals("2200") || n.startsWith("221") && n.length() == 4
                        || n.startsWith("22") && n.length() == 8)
                .count();
    }

    /**
     * Character creation data for Evan is in the Etc tree the server actually validates against.
     * {@code MakeCharInfoValidator:17-23} builds one {@code MakeCharInfo} per creatable class out
     * of {@code Etc.wz/MakeCharInfo.img}; without these nodes ticket 15 has nothing to validate an
     * Evan against. Ticket 04 declined all of {@code Etc.wz} and left exactly this block behind.
     */
    @Test
    void evanCharacterCreationDataIsPresent() {
        Data makeChar = wz("Etc.wz").getData("MakeCharInfo.img");
        assertNotNull(makeChar.getChildByPath("EvanCharMale"), "MakeCharInfo.img/EvanCharMale");
        assertNotNull(makeChar.getChildByPath("EvanCharFemale"), "MakeCharInfo.img/EvanCharFemale");
        assertNotNull(makeChar.getChildByPath("Name/EvanCharMale"), "MakeCharInfo.img/Name/EvanCharMale");
        assertNotNull(makeChar.getChildByPath("Name/EvanCharFemale"), "MakeCharInfo.img/Name/EvanCharFemale");
        // the six the live client already had must survive an additive merge
        for (String kept : new String[]{"Info", "PremiumCharMale", "PremiumCharFemale", "OrientCharMale", "OrientCharFemale"}) {
            assertNotNull(makeChar.getChildByPath(kept), "pre-existing " + kept + " lost");
        }
    }

    /**
     * The constant tables the whole Evan path rides on, pinned in one place.
     * <p>
     * <b>Read this as a change-detector, not as evidence</b>, because two of the three assertions
     * restate data rather than derive it. It earns its place anyway: {@code !job} is the only GM
     * route to an Evan and its guard is now {@code Job.getById(...) != null}
     * (see {@code JobCommand}), which cannot be exercised from a unit test — there is no
     * {@code Client} — so this asserts the condition that guard evaluates instead. {@code hasSPTable}
     * is what routes an Evan onto the ten-slot SP array *and* what gates {@code createDragon()}, and
     * {@code getSkillBook} is what makes that array address the right tab.
     */
    @Test
    void theConstantTablesTheEvanPathRidesOn() {
        assertEquals(Job.EVAN, Job.getById(2001));
        assertEquals(Job.EVAN1, Job.getById(2200));
        assertEquals(Job.EVAN10, Job.getById(2218));
        assertNull(Job.getById(2219), "2219 is not a job and must still be rejected");
        assertNull(Job.getById(-1));

        assertTrue(GameConstants.hasSPTable(Job.EVAN), "job 2001 must use the extended SP table");
        assertFalse(GameConstants.hasSPTable(Job.MAGICIAN), "negative control");
        for (int job : EVAN_JOB_IMAGES) {
            Job j = Job.getById(job);
            assertNotNull(j, "!job " + job + " would be rejected");
            assertTrue(GameConstants.hasSPTable(j), "job " + job + " off the extended SP table");
        }

        assertEquals(0, GameConstants.getSkillBook(2200));
        assertEquals(1, GameConstants.getSkillBook(2210));
        assertEquals(9, GameConstants.getSkillBook(2218));
    }

    /**
     * The dragon-removal defect, demonstrated on the real {@link MapleMap} rather than argued.
     * <p>
     * {@code addMapObject} keys the object by a freshly minted map OID, and
     * {@code removeMapObject(MapObject)} looks that key up through {@code getObjectId()}. {@link
     * Dragon} used to override {@code getObjectId()} to return its owner's character id, so the
     * remove looked up a key the map never used and silently did nothing — leaving a dragon in
     * {@code mapobjects} after its owner logged out or changed map, for the next player entering
     * to be shown by {@code sendObjectPlacement} (DRAGON is a non-ranged type,
     * {@code MapleMap:3067}). The two cases below are the same object with and without that
     * override.
     */
    @Test
    void removeMapObjectOnlyWorksWhenGetObjectIdIsTheMapsOwn() {
        MapleMap map = new MapleMap(100000000, 0, 0, 0, 1);

        FakeMapObject wellBehaved = new FakeMapObject(0);
        map.addMapObject(wellBehaved);
        int oid = wellBehaved.getObjectId();
        assertSame(wellBehaved, map.getMapObject(oid));
        map.removeMapObject(wellBehaved);
        assertNull(map.getMapObject(oid), "a map object keyed on its own OID is removable");

        // Negative control: the shape Dragon used to have. Everything is identical except that
        // getObjectId() reports something the map never filed it under.
        FakeMapObject shadowsItsId = new FakeMapObject(4242);
        map.addMapObject(shadowsItsId);
        int realOid = shadowsItsId.mapAssignedId();
        assertEquals(4242, shadowsItsId.getObjectId(), "the object reports a foreign id");
        map.removeMapObject(shadowsItsId);
        assertSame(shadowsItsId, map.getMapObject(realOid),
                "an object reporting a foreign id survives its own removal — the bug the fix closes");
    }

    /**
     * The other door into the same ghost dragon, found in review of this ticket.
     * <p>
     * {@code Character.map} is <b>never cleared when a player leaves a map</b> — entering the Cash
     * Shop or MTS runs {@code MapleMap.removePlayer} but leaves the field pointing at the map they
     * came from. So {@code createDragon()} on an away player (e.g. {@code !job <name> 2210} aimed at
     * someone sitting in the Cash Shop) would register a dragon into a map its owner is not in,
     * <em>after</em> the removal that would have cleaned it up — and nothing would ever take it out.
     * The guard is inside {@link MapleMap#spawnDragon} rather than at the call site, so every caller
     * gets it; {@code addPlayer} adds the player to {@code characters} long before its own call.
     */
    @Test
    void spawnDragonIgnoresAnOwnerWhoIsNotInThisMap() {
        MapleMap map = new MapleMap(100000000, 0, 0, 0, 1);
        Character absentOwner = mock(Character.class);
        Dragon dragon = mock(Dragon.class);
        when(dragon.getOwner()).thenReturn(absentOwner);
        // Enough for the UNGUARDED path to run to completion, so that removing the guard fails on
        // the assertion below rather than on an incidental NPE inside PacketCreator.spawnDragon.
        when(absentOwner.getJob()).thenReturn(Job.EVAN2);
        when(dragon.getPosition()).thenReturn(new Point(0, 0));

        map.spawnDragon(dragon);

        // nothing filed, and the dragon was never even asked where it should stand
        assertTrue(map.getMapObjects().isEmpty(), "a dragon was filed for a player who is not here");
        verify(dragon, never()).setPosition(any());
    }

    /**
     * And the pin: {@link Dragon} must not re-acquire that override. A change-detector, and
     * deliberately one — {@link #removeMapObjectOnlyWorksWhenGetObjectIdIsTheMapsOwn} proves the
     * mechanism on {@link MapleMap} but says nothing about {@link Dragon}, and returning the owner's
     * character id here is the intuitive-looking implementation that was wrong for two years.
     */
    @Test
    void dragonDoesNotOverrideGetObjectId() {
        assertThrows(NoSuchMethodException.class, () -> Dragon.class.getDeclaredMethod("getObjectId"),
                "Dragon overrides getObjectId() again — see removeMapObjectOnlyWorksWhenGetObjectIdIsTheMapsOwn");
    }

    private static final class FakeMapObject extends AbstractMapObject {
        private final int reportedId;

        private FakeMapObject(int reportedId) {
            this.reportedId = reportedId;
        }

        /** What {@code addMapObject} actually filed this under, whatever we report. */
        int mapAssignedId() {
            return super.getObjectId();
        }

        @Override
        public int getObjectId() {
            return reportedId == 0 ? super.getObjectId() : reportedId;
        }

        @Override
        public MapObjectType getType() {
            return MapObjectType.DRAGON;
        }

        @Override
        public void sendSpawnData(Client client) {
        }

        @Override
        public void sendDestroyData(Client client) {
        }
    }
}
