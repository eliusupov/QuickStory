package client;

import constants.skills.Evan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * v83 legacy defect - not a v84 parity gap. Echo of Hero has no {@code add-list} row in any
 * category, so v84 added nothing to it; the buff has never been read since v83.
 *
 * <p>{@code StatEffect} writes {@link BuffStat#ECHO_OF_HERO} at the skill's {@code x}, a
 * percentage, and {@code applyEchoOfHero} delivers it map-wide. Both attack totals must consult
 * it after their respective flat buffs.
 *
 * <p>Reflection throughout: the constructor, the buff map and {@code reapplyLocalStats} are all
 * private, and there is no seam to a {@code Character} that does not also need a client, a world
 * and a database.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=EchoOfHeroWatkRealLoad
 * </pre>
 *
 * <p><strong>Not a {@code *Test} class on purpose</strong>, same reason as its siblings:
 * {@link WZFiles#DIRECTORY} is {@code static final} and resolved once per JVM.
 */
class EchoOfHeroWatkRealLoad {

    /** Whatever base watk the equipment gives; the buff is a percentage of it. */
    private static final int BASE_WATK = 100;

    private static final int BASE_MATK = 100;

    @BeforeAll
    static void loadSkills() {
        SkillFactory.loadAllSkills();
    }

    /** A character carrying only base attack totals, plus {@code buff} if non-null. */
    private static int[] totalsWith(Integer buff) throws Exception {
        Constructor<Character> ctor = Character.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Character chr = ctor.newInstance();

        Field equipwatk = Character.class.getDeclaredField("equipwatk");
        equipwatk.setAccessible(true);
        equipwatk.setInt(chr, BASE_WATK);

        Field equipmagic = Character.class.getDeclaredField("equipmagic");
        equipmagic.setAccessible(true);
        equipmagic.setInt(chr, BASE_MATK);

        // equipchanged starts true, and recalcEquipStats then recomputes equipwatk from the
        // (empty) EQUIPPED inventory, zeroing whatever was planted above. Clear it so the base
        // watk survives into localwatk.
        Field equipchanged = Character.class.getDeclaredField("equipchanged");
        equipchanged.setAccessible(true);
        equipchanged.setBoolean(chr, false);

        if (buff != null) {
            Class<?> holderClass = Class.forName("client.Character$BuffStatValueHolder");
            Constructor<?> holderCtor = holderClass.getDeclaredConstructor(
                    server.StatEffect.class, long.class, int.class);
            holderCtor.setAccessible(true);

            Field effects = Character.class.getDeclaredField("effects");
            effects.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<BuffStat, Object> map = (Map<BuffStat, Object>) effects.get(chr);
            map.put(BuffStat.ECHO_OF_HERO,
                    holderCtor.newInstance((server.StatEffect) null, System.currentTimeMillis(),
                            buff.intValue()));
        }

        Method reapply = Character.class.getDeclaredMethod("reapplyLocalStats");
        reapply.setAccessible(true);
        reapply.invoke(chr);

        return new int[]{chr.getTotalWatk(), chr.getTotalMagic()};
    }

    /** The real Evan WZ value must reach both physical and magical local attack totals. */
    @Test
    void echoOfHeroRaisesBothAttackTotalsByItsRealWzPercentage() throws Exception {
        int echoPercent = SkillFactory.getSkill(Evan.ECHO_OF_HERO).getEffect(1).getX();
        int[] totals = totalsWith(echoPercent);

        assertAll(
                () -> assertEquals(BASE_WATK + (BASE_WATK * echoPercent) / 100, totals[0],
                        "Echo of Hero did not reach localwatk"),
                () -> assertEquals(BASE_MATK + (BASE_MATK * echoPercent) / 100, totals[1],
                        "Echo of Hero did not reach localmagic")
        );
    }

    /** The null branch: a character without the buff must be exactly where it was. */
    @Test
    void aCharacterWithoutTheBuffIsUnchanged() throws Exception {
        int[] totals = totalsWith(null);
        assertAll(
                () -> assertEquals(BASE_WATK, totals[0], "localwatk moved without Echo of Hero"),
                () -> assertEquals(BASE_MATK, totals[1], "localmagic moved without Echo of Hero")
        );
    }
}
