package scripting;

import config.ServerConfig;
import config.YamlConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two things that made timed events dead on this server:
 * the recurring window math in scripts/event/2xEvent.js, and the EVENT_END_TIMESTAMP gate that a
 * hardcoded 2015 epoch had left permanently closed.
 */
class TimedExpEventScheduleTest {
    private static final long TWO_HOURS = 2 * 3600000L;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private static long millisOf(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Evaluates 2xEvent.js and exposes nextWindowFrom through primitive-returning helpers. */
    private static Invocable loadScript() throws ScriptException {
        ScriptEngine engine = new AbstractScriptManager() {}.getInvocableScriptEngine("event/2xEvent.js");
        engine.eval("""
                function testSettings(dayIndex, hour, durationMs, multiplier) {
                    return {days: [dayIndex], hour: hour, durationMs: durationMs, multiplier: multiplier};
                }
                function testWindowStart(now, settings) {
                    var w = nextWindowFrom(now, settings);
                    return w == null ? -1 : w.start;
                }
                function testWindowEnd(now, settings) {
                    var w = nextWindowFrom(now, settings);
                    return w == null ? -1 : w.end;
                }
                function testSettingsDays() {
                    var s = readSettings();
                    return s == null ? "disabled" : s.days.join(",");
                }
                function testSettingsMultiplier() {
                    var s = readSettings();
                    return s == null ? -1 : s.multiplier;
                }
                """);
        return (Invocable) engine;
    }

    /** Runs body with the timed-exp-event config temporarily overridden, then restores it. */
    private static void withEventConfig(String days, int hour, int durationHours, int multiplier,
                                        ThrowingRunnable body) throws Exception {
        ServerConfig server = YamlConfig.config.server;
        String origDays = server.TIMED_EXP_EVENT_DAYS;
        int origHour = server.TIMED_EXP_EVENT_START_HOUR;
        int origDuration = server.TIMED_EXP_EVENT_DURATION_HOURS;
        int origMultiplier = server.TIMED_EXP_EVENT_MULTIPLIER;
        try {
            server.TIMED_EXP_EVENT_DAYS = days;
            server.TIMED_EXP_EVENT_START_HOUR = hour;
            server.TIMED_EXP_EVENT_DURATION_HOURS = durationHours;
            server.TIMED_EXP_EVENT_MULTIPLIER = multiplier;
            body.run();
        } finally {
            server.TIMED_EXP_EVENT_DAYS = origDays;
            server.TIMED_EXP_EVENT_START_HOUR = origHour;
            server.TIMED_EXP_EVENT_DURATION_HOURS = origDuration;
            server.TIMED_EXP_EVENT_MULTIPLIER = origMultiplier;
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Loads the script with {@code em}, {@code getWorld} and {@code announce} stubbed, so the
     * arm/start/stop chain can be driven with no server, channel or world behind it.
     * {@code testBoostedRate()} returns -1 until something calls setExpRate.
     */
    private static Invocable loadScriptWithStubs() throws ScriptException {
        ScriptEngine engine = new AbstractScriptManager() {}.getInvocableScriptEngine("event/2xEvent.js");
        engine.eval("""
                var stubbedRate = -1;
                var stubbedSchedules = [];
                var em = {
                    schedule: function (method, delay) { stubbedSchedules.push(method); return null; },
                    getChannelServer: function () { return {getId: function () { return 1; }}; }
                };
                function getWorld() {
                    return {
                        getExpRate: function () { return 1; },
                        setExpRate: function (rate) { stubbedRate = rate; }
                    };
                }
                function announce(message) {}
                function testBoostedRate() { return stubbedRate; }
                function testScheduled() { return stubbedSchedules.join(","); }
                """);
        return (Invocable) engine;
    }

    private static int boostedRate(Invocable script) throws Exception {
        return ((Number) script.invokeFunction("testBoostedRate")).intValue();
    }

    /** JS Date.getDay() is 0=Sunday; java.time DayOfWeek is 1=Monday..7=Sunday. */
    private static int jsDayIndex(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue() % 7;
    }

    private static Object saturdayAt20(Invocable script) throws Exception {
        return script.invokeFunction("testSettings", jsDayIndex(DayOfWeek.SATURDAY), 20, TWO_HOURS, 2);
    }

    private static long startFor(Invocable script, LocalDateTime now, Object settings) throws Exception {
        return ((Number) script.invokeFunction("testWindowStart", (double) millisOf(now), settings)).longValue();
    }

    private static long endFor(Invocable script, LocalDateTime now, Object settings) throws Exception {
        return ((Number) script.invokeFunction("testWindowEnd", (double) millisOf(now), settings)).longValue();
    }

    @Test
    void schedulesTheUpcomingWindowWhenTheEventIsDaysAway() throws Exception {
        Invocable script = loadScript();
        Object settings = saturdayAt20(script);

        // Wednesday noon -> the coming Saturday at 20:00.
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 12, 0);
        assertEquals(DayOfWeek.WEDNESDAY, now.getDayOfWeek(), "test fixture must start on a Wednesday");

        assertEquals(millisOf(LocalDateTime.of(2026, 8, 22, 20, 0)), startFor(script, now, settings));
    }

    @Test
    void picksUpAWindowAlreadyUnderway() throws Exception {
        Invocable script = loadScript();
        Object settings = saturdayAt20(script);

        // Booting at 21:00 on the event day must resume the running window, not skip a week.
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 21, 0);
        long nowMillis = millisOf(now);

        assertTrue(startFor(script, now, settings) < nowMillis, "window should have already started");
        assertTrue(endFor(script, now, settings) > nowMillis, "window should still be running");
        assertEquals(millisOf(LocalDateTime.of(2026, 8, 22, 22, 0)), endFor(script, now, settings));
    }

    @Test
    void rollsOverToNextWeekOnceTheWindowHasClosed() throws Exception {
        Invocable script = loadScript();
        Object settings = saturdayAt20(script);

        // 23:00, an hour after the window closed -> the following Saturday.
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 23, 0);

        assertEquals(millisOf(LocalDateTime.of(2026, 8, 29, 20, 0)), startFor(script, now, settings));
    }

    @Test
    void findsAWindowThatStartedBeforeMidnight() throws Exception {
        Invocable script = loadScript();
        // 23:00 Saturday + 2h runs until 01:00 Sunday. Searching only from "today" forward would miss
        // it entirely at 00:30 and report the event as not running.
        Object settings = script.invokeFunction("testSettings", jsDayIndex(DayOfWeek.SATURDAY), 23, TWO_HOURS, 2);

        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 0, 30);
        assertEquals(DayOfWeek.SUNDAY, now.getDayOfWeek(), "test fixture must land after midnight");
        long nowMillis = millisOf(now);

        assertEquals(millisOf(LocalDateTime.of(2026, 8, 22, 23, 0)), startFor(script, now, settings));
        assertTrue(endFor(script, now, settings) > nowMillis, "the window should still be running");
    }

    @Test
    void findsAMultiDayWindowFromItsLaterDays() throws Exception {
        Invocable script = loadScript();
        // A 48h window opened on Saturday is still running on Monday morning.
        Object settings = script.invokeFunction("testSettings", jsDayIndex(DayOfWeek.SATURDAY), 20, 48 * 3600000L, 2);

        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 9, 0);
        assertEquals(DayOfWeek.MONDAY, now.getDayOfWeek(), "test fixture must land two days in");

        assertEquals(millisOf(LocalDateTime.of(2026, 8, 22, 20, 0)), startFor(script, now, settings));
    }

    @Test
    void startDoesNotBoostWhenItFiresOutsideItsWindow() throws Exception {
        Invocable script = loadScriptWithStubs();
        // The scheduler counts delays down on a simulated clock that lags wall time, so "start" can
        // fire after its window closed. nextWindow() then returns the FOLLOWING window - boosting on
        // that would raise the rate now and schedule the stop up to a week out.
        DayOfWeek notToday = LocalDateTime.now().getDayOfWeek().plus(2);

        withEventConfig(notToday.name(), 20, 2, 2, () -> {
            script.invokeFunction("start");

            assertEquals(-1, boostedRate(script), "must not touch the exp rate outside its window");
            assertEquals("start", script.invokeFunction("testScheduled"),
                    "must re-aim at the upcoming window instead");
        });
    }

    @Test
    void startBoostsWhenItsWindowIsActuallyOpen() throws Exception {
        Invocable script = loadScriptWithStubs();
        // Control for the test above: a window open right now must still boost, so the guard cannot
        // be satisfied by simply never starting.
        DayOfWeek today = LocalDateTime.now().getDayOfWeek();

        withEventConfig(today.name(), 0, 24, 3, () -> {
            script.invokeFunction("start");

            assertEquals(3, boostedRate(script), "base rate 1 times the configured multiplier");
            assertEquals("stop", script.invokeFunction("testScheduled"), "must schedule its own end");
        });
    }

    @Test
    void cancelScheduleRestoresTheRate() throws Exception {
        Invocable script = loadScriptWithStubs();
        // reloadEventScriptManager() calls cancelSchedule() and then builds a NEW engine with
        // baseExpRate back at -1. If the rate is left boosted, the next start() captures it as its
        // base and compounds: 2x -> 4x -> 8x.
        DayOfWeek today = LocalDateTime.now().getDayOfWeek();

        withEventConfig(today.name(), 0, 24, 3, () -> {
            script.invokeFunction("start");
            assertEquals(3, boostedRate(script));

            script.invokeFunction("cancelSchedule");
            assertEquals(1, boostedRate(script), "cancelSchedule must put the world rate back");
        });
    }

    @Test
    void shippedConfigLeavesTheEventDisabled() throws Exception {
        Invocable script = loadScript();

        // Guards the promise made in config.yaml and the ticket: out of the box, nothing schedules
        // and world exp rates are never touched.
        assertEquals("disabled", script.invokeFunction("testSettingsDays"));
    }

    @Test
    void readsWeekdayNamesFromConfig() throws Exception {
        Invocable script = loadScript();

        withEventConfig(" saturday , SUNDAY ", 20, 2, 2, () ->
                // Case-insensitive, whitespace-tolerant; JS day indices are 0=Sunday, 6=Saturday.
                assertEquals("6,0", script.invokeFunction("testSettingsDays")));
    }

    @Test
    void treatsUnusableConfigAsDisabledRatherThanScheduling() throws Exception {
        Invocable script = loadScript();

        withEventConfig("NOTADAY", 20, 2, 2, () ->
                assertEquals("disabled", script.invokeFunction("testSettingsDays"),
                        "an unrecognised weekday must disable, not schedule an empty window"));
        withEventConfig("SATURDAY", 20, 0, 2, () ->
                assertEquals("disabled", script.invokeFunction("testSettingsDays"),
                        "a zero-length window must disable"));
        withEventConfig("SATURDAY", 20, 2, 1, () ->
                assertEquals("disabled", script.invokeFunction("testSettingsDays"),
                        "a multiplier of 1 boosts nothing, so it must disable"));
        withEventConfig("SATURDAY", 24, 2, 2, () ->
                assertEquals("disabled", script.invokeFunction("testSettingsDays"),
                        "hour 24 would normalise into the next day and shift the whole schedule"));
        withEventConfig("SATURDAY", 20, 2, 3, () ->
                assertEquals(3, ((Number) script.invokeFunction("testSettingsMultiplier")).intValue(),
                        "a usable multiplier must survive the round trip"));
    }

    @Test
    void eventWindowIsOpenWhenNoEndTimestampIsConfigured() {
        long original = YamlConfig.config.server.EVENT_END_TIMESTAMP;
        try {
            YamlConfig.config.server.EVENT_END_TIMESTAMP = 0;
            assertTrue(AbstractPlayerInteraction.isWithinEventWindow(), "0 must mean no end date");

            YamlConfig.config.server.EVENT_END_TIMESTAMP = 1428897600000L; // the old shipped 2015 epoch
            assertFalse(AbstractPlayerInteraction.isWithinEventWindow(), "a past end date must close the window");

            YamlConfig.config.server.EVENT_END_TIMESTAMP = System.currentTimeMillis() + 3600000L;
            assertTrue(AbstractPlayerInteraction.isWithinEventWindow(), "a future end date must leave it open");
        } finally {
            YamlConfig.config.server.EVENT_END_TIMESTAMP = original;
        }
    }
}
