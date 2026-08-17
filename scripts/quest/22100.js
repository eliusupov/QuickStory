/*
 * Evan job advancement 1st - "Dragon Master 1st Job Advancement", quest 22100.
 * Mir (npc 1013000): 2001 -> 2200 at level 10, after quest 22007.
 *
 * v84 Check.img/22100 = npc 1013000, lvmin 10, job 2001, quest 22007 state 2,
 * startscript q22100s, normalAutoStart 1, and NO endscript; Act.img/22100 and Say.img/22100
 * are both empty. Nothing in the data changes job - if this file does not, nothing does.
 *
 * Why the gates below are load bearing, and not defensive noise (the other nine files point
 * back here): QuestActionHandler takes the quest id straight off a client packet, and
 * Quest.canStart can only enforce lvmin/job/prereq once Quest.wz actually carries 22100-22109.
 * That merge is ticket 13's; until it lands there is no server-side gate but this one.
 *
 * orion-server, the port source, is no help here: its 22100-22109 are ten identical
 * forceStart/forceComplete no-ops that never change job.
 */
var status = -1;

function start(mode, type, selection) {
    if (mode != 1) {    // End Chat, or a Prev this script never offers
        qm.dispose();
        return;
    }

    // Gates on every forward screen, not just the first: the client says which screen we are
    // on, so a gate that only guards screen 0 guards nothing.
    if (qm.getJob().getId() != 2001) {
        qm.dispose();   // wrong job, or already advanced - nothing to do
        return;
    }
    if (qm.getLevel() < 10) {
        qm.sendOk("Not yet, master. Come back when you have reached #blevel 10#k.");
        qm.dispose();
        return;
    }
    status++;

    if (status == 0) {
        qm.sendNext("Master, I can feel it... I'm about to grow. Are you ready?");
    } else if (status == 1) {
        qm.changeJobById(2200);
        // Moves the beginner's auto-assigned STR/DEX into INT, as Aran's 21101.js and the five
        // Explorer 1st-job NPCs do. resetStats' case 2200 leaves the ten-slot SP array alone.
        qm.resetStats();
        qm.forceStartQuest();
        qm.forceCompleteQuest();
        // sendNext, not sendNextPrev: the job gate above now rejects this character, so a
        // Prev button would dead-end into dispose().
        qm.sendNext("Baby Dragon Mir has been born from the Dragon Egg.\r\n\r\nYou have received #bSP#k and #bAP#k. Open the Skill window to see what I can do now, master!");
    } else {
        qm.dispose();
    }
}