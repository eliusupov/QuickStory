/*
 * Evan job advancement 2nd - "Dragon Master 2nd Job Advancement", quest 22101.
 * Mir (npc 1013000): 2200 -> 2210 at level 20, after quest 22100.
 *
 * v84 Check.img/22101 = npc 1013000, lvmin 20, job 2200, quest 22100 state 2,
 * startscript q22101s, normalAutoStart 1, and NO endscript; Act.img/22101 and Say.img/22101
 * are both empty. Nothing in the data changes job - if this file does not, nothing does.
 * See scripts/quest/22100.js for why the gates below are load bearing.
 */
var status = -1;

function start(mode, type, selection) {
    if (mode != 1) {    // End Chat, or a Prev this script never offers
        qm.dispose();
        return;
    }

    // Gates on every forward screen, not just the first: the client says which screen we are
    // on, so a gate that only guards screen 0 guards nothing.
    if (qm.getJob().getId() != 2200) {
        qm.dispose();   // wrong job, or already advanced - nothing to do
        return;
    }
    if (qm.getLevel() < 20) {
        qm.sendOk("Not yet, master. Come back when you have reached #blevel 20#k.");
        qm.dispose();
        return;
    }
    status++;

    if (status == 0) {
        qm.sendNext("Master, I can feel it... I'm about to grow. Are you ready?");
    } else if (status == 1) {
        qm.changeJobById(2210);
        qm.forceStartQuest();
        qm.forceCompleteQuest();
        // ponytail: no resetStats() here, unlike the Cygnus advancement scripts. It has no
        // Evan branch (Character.java:7954) and would rewrite the ten-slot SP array through
        // updateStrDexIntLukSp. changeJob() already grants Evan's SP, AP, slots, HP/MP and
        // respawns the dragon.
        // sendNext, not sendNextPrev: the job gate above now rejects this character, so a
        // Prev button would dead-end into dispose().
        qm.sendNext("Mir has grown?! Mir has grown bigger and taken off the egg shell he was wearing as a hat... Apparently, dragons grow too.\r\n\r\nYou have received #bSP#k and #bAP#k. Open the Skill window to see what I can do now, master!");
    } else {
        qm.dispose();
    }
}