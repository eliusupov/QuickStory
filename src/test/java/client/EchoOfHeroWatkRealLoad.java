package client;

import org.junit.jupiter.api.Test;
import provider.wz.WZFiles;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * v83 legacy defect - not a v84 parity gap. Echo of Hero has no {@code add-list} row in any
 * category, so v84 added nothing to it; the buff has never been read since v83.
 *
 * <p>{@code StatEffect} writes {@link BuffStat#ECHO_OF_HERO} at the skill's {@code x} - 4, a
 * percentage - and {@code applyEchoOfHero} delivers it map-wide. Nothing then consulted it:
 * {@code reapplyLocalStats} had no {@code ECHO_OF_HERO} reader, so {@code localwatk} never saw the
 * 4% and the buff was decorative. It is applied now, in the same idiom as the flat WATK buff
 * immediately above it.
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

    /** Echo of Hero's x for every one of its four job flavours. */
    private static final int ECHO_PERCENT = 4;

    /** A character carrying only equipment watk, plus {@code buff} if non-null. */
    private static int totalWatkWith(Integer buff) throws Exception {
        Constructor<Character> ctor = Character.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Character chr = ctor.newInstance();

        Field equipwatk = Character.class.getDeclaredField("equipwatk");
        equipwatk.setAccessible(true);
        equipwatk.setInt(chr, BASE_WATK);

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

        return chr.getTotalWatk();
    }

    /** The regression: unfixed this is 100, because nothing ever read the buff. */
    @Test
    void echoOfHeroRaisesLocalWatkByItsPercentage() throws Exception {
        assertEquals(BASE_WATK + (BASE_WATK * ECHO_PERCENT) / 100, totalWatkWith(ECHO_PERCENT),
                "Echo of Hero at " + ECHO_PERCENT + "% did not reach localwatk");
    }

    /** The null branch: a character without the buff must be exactly where it was. */
    @Test
    void aCharacterWithoutTheBuffIsUnchanged() throws Exception {
        assertEquals(BASE_WATK, totalWatkWith(null),
                "localwatk moved on a character that holds no ECHO_OF_HERO buff");
    }
}
