/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
/**
 -- Odin JavaScript --------------------------------------------------------------------------------
 Timed world-wide EXP event.
 -- Author --------------------------------------------------------------------------------------
 Twdtwd, rewritten for recurring config-driven scheduling.

 DEFAULT OFF. Driven entirely by config.yaml:
     TIMED_EXP_EVENT_DAYS             comma-separated weekday names; EMPTY (the default) = never runs
     TIMED_EXP_EVENT_START_HOUR       hour 0-23, server local time
     TIMED_EXP_EVENT_DURATION_HOURS   window length
     TIMED_EXP_EVENT_MULTIPLIER       world exp rate is multiplied by this during the window

 Recurs by re-arming itself with em.schedule(), the same relative-delay pattern the AreaBoss
 spawners use. It never stores an absolute epoch, so it cannot rot the way the old hardcoded
 April-2015 timestamps did.
 **/

var timer1;
var timer2;

var baseExpRate = -1;

const WEEKDAYS = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];

function init() {
    // The exp rate is world-wide, so only one channel may drive it.
    if (em.getChannelServer().getId() != 1) {
        return;
    }
    arm();
}

function readSettings() {
    const YamlConfig = Java.type('config.YamlConfig');
    var server = YamlConfig.config.server;

    var raw = server.TIMED_EXP_EVENT_DAYS;
    if (raw == null || String(raw).trim().length == 0) {
        return null;    // disabled
    }

    var days = [];
    var names = String(raw).toUpperCase().split(",");
    for (var i = 0; i < names.length; i++) {
        var idx = WEEKDAYS.indexOf(names[i].trim());
        if (idx >= 0) {
            days.push(idx);
        }
    }
    if (days.length == 0) {
        return null;
    }

    var hour = server.TIMED_EXP_EVENT_START_HOUR;
    var durationHours = server.TIMED_EXP_EVENT_DURATION_HOURS;
    var multiplier = server.TIMED_EXP_EVENT_MULTIPLIER;
    // An out-of-range hour would be normalised into a different DAY by the Date constructor, silently
    // shifting the whole schedule, so refuse it rather than run the event at the wrong time.
    if (hour < 0 || hour > 23 || durationHours <= 0 || multiplier <= 1) {
        return null;    // a bad hour, zero-length or non-boosting window is the same as disabled
    }

    return {
        days: days,
        hour: hour,
        durationMs: durationHours * 3600000,
        multiplier: multiplier
    };
}

/**
 * First event window whose END is still in the future, searching today plus the next 7 days.
 * Pure and parameterised so it can be unit tested without a running server.
 * Returns {start, end, multiplier} in epoch millis, or null when the event is disabled.
 */
function nextWindowFrom(nowMillis, settings) {
    if (settings == null) {
        return null;
    }
    var now = new Date(nowMillis);
    // Start far enough back that a window which began on an earlier calendar day is still found:
    // START_HOUR 23 with a 2h duration is still running at 00:30 the next day, and a duration over
    // 24h spans several. Ascending offsets still return the earliest window, and the "end > now"
    // filter below still discards the ones that have already finished.
    var daysBack = Math.ceil(settings.durationMs / 86400000);
    for (var offset = -daysBack; offset <= 7; offset++) {
        var start = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset, settings.hour, 0, 0, 0);
        if (settings.days.indexOf(start.getDay()) < 0) {
            continue;
        }
        var end = start.getTime() + settings.durationMs;
        if (end > nowMillis) {
            return {start: start.getTime(), end: end, multiplier: settings.multiplier};
        }
    }
    return null;
}

function nextWindow() {
    return nextWindowFrom(new Date().getTime(), readSettings());
}

function arm() {
    var window = nextWindow();
    if (window == null) {
        return;
    }
    // A window already underway (server booted mid-event) arms with delay 0 and starts on the next tick.
    var delay = window.start - new Date().getTime();
    timer1 = em.schedule("start", delay > 0 ? delay : 0);
}

function getWorld() {
    const Server = Java.type('net.server.Server');
    return Server.getInstance().getWorld(em.getChannelServer().getWorld());
}

function announce(message) {
    const PacketCreator = Java.type('tools.PacketCreator');
    getWorld().broadcastPacket(PacketCreator.serverNotice(6, message));
}

function start() {
    var window = nextWindow();
    if (window == null) {
        return;     // disabled between arming and firing
    }
    // The scheduler counts our delay down on Server.getCurrentTime(), a simulated clock that only
    // ever lags wall time (CharacterDiseaseTask advances it by a fixed step; it syncs for real just
    // once at TimerManager start). So "start" can fire after its window has closed, and nextWindow()
    // then hands back the FOLLOWING window. Boosting on that would raise the rate immediately and
    // schedule the stop up to a week out. Re-aim instead of boosting.
    if (window.start > new Date().getTime()) {
        arm();
        return;
    }

    var world = getWorld();
    // Only capture the base rate if we are not already running. A start() that fired twice without an
    // intervening stop() would otherwise capture the already-boosted rate and stop() would "restore"
    // it, latching the multiplier in permanently.
    if (baseExpRate < 0) {
        baseExpRate = world.getExpRate();
    }
    world.setExpRate(baseExpRate * window.multiplier);
    announce("The Emergency XP Pool (EXP) is online! Experience gained is multiplied by "
            + window.multiplier + " until the pool runs dry.");

    var remaining = window.end - new Date().getTime();
    timer2 = em.schedule("stop", remaining > 0 ? remaining : 1000);
}

function stop() {
    if (baseExpRate >= 0) {   // only announce if we actually had the rate boosted
        getWorld().setExpRate(baseExpRate);
        baseExpRate = -1;
        announce("The Emergency XP Pool (EXP) has run out of juice and needs to recharge, so the EXP rate is back to normal.");
    }
    arm();  // schedule the following occurrence
}

function cancelSchedule() {
    // Restore the rate before the engine goes away. reloadEventScriptManager() (the !reloadevents GM
    // command, and channel shutdown) calls this and then builds a BRAND NEW script engine, where
    // baseExpRate is back to -1 while the world is still boosted. Without this, the next start()
    // would capture the boosted rate as its base and compound it: 2x -> 4x -> 8x, permanently.
    if (baseExpRate >= 0) {
        getWorld().setExpRate(baseExpRate);
        baseExpRate = -1;
    }
    if (timer1 != null) {
        timer1.cancel(true);
    }
    if (timer2 != null) {
        timer2.cancel(true);
    }
}

// ---------- FILLER FUNCTIONS ----------

function dispose() {}

function setup(eim, leaderid) {}

function monsterValue(eim, mobid) {return 0;}

function disbandParty(eim, player) {}

function playerDisconnected(eim, player) {}

function playerEntry(eim, player) {}

function monsterKilled(mob, eim) {}

function scheduledTimeout(eim) {}

function afterSetup(eim) {}

function changedLeader(eim, leader) {}

function playerExit(eim, player) {}

function leftParty(eim, player) {}

function clearPQ(eim) {}

function allMonstersDead(eim) {}

function playerUnregistered(eim, player) {}
