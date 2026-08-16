/*
 * Evan job advancement 10th - "Dragon Master 10th Job Advancement", quest 22109.
 * Mir (npc 1013000): 2217 -> 2218 at level 160, after quest 22108.
 *
 * v84 Check.img/22109 = npc 1013000, lvmin 160, job 2217, quest 22108 state 2,
 * startscript q22109s, normalAutoStart 1, and NO endscript; Act.img/22109 and Say.img/22109
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
    if (qm.getJob().getId() != 2217) {
        qm.dispose();   // wrong job, or already advanced - nothing to do
        return;
    }
    if (qm.getLevel() < 160) {
        qm.sendOk("Not yet, master. Come back when you have reached #blevel 160#k.");
        qm.dispose();
        return;
    }
    status++;

    if (status == 0) {
        qm.sendNext("Master, I can feel it... I'm about to grow. Are you ready?");
    } else if (status == 1) {
        qm.changeJobById(2218);
        qm.forceStartQuest();
        qm.forceCompleteQuest();
        // ponytail: no resetStats() here, unlike the Cygnus advancement scripts. It has no
        // Evan branch (Character.java:7954) and would rewrite the ten-slot SP array through
        // updateStrDexIntLukSp. changeJob() already grants Evan's SP, AP, slots, HP/MP and
        // respawns the dragon.
        // sendNext, not sendNextPrev: the job gate above now rejects this character, so a
        // Prev button would dead-end into dispose().
        qm.sendNext("Mir is now fully grown. Is this what an Onyx Dragon truly looks like? Looking simultaneously threatening and friendly is a privilege reserved for Dragons.\r\n\r\nYou have received #bSP#k and #bAP#k. Open the Skill window to see what I can do now, master!");
    } else {
        qm.dispose();
    }
}