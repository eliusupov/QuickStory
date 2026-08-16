/* Quest 28354 - "The Shadow Knight's Request for Help" (v84, ticket 09).
 * Both halves at Shadow Knight Rene (9201144), after quest 28353 is complete, Lv.15+.
 * Completion requires 1 #t4032639#; Act.img/28354/1 removes it. The declared reward is
 * "Popularity 3" (QuestInfo.img/28354/rewardSummary) and there is no fame node in Act, so
 * GMS's own script granted it - this one does the same and nothing more.
 * Check.img/28354/0/end is "2010042100" - the event window has closed.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    qm.dispose();
}

function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.gainItem(4032639, -1);
    qm.gainFame(3);
    qm.forceCompleteQuest();
    qm.dispose();
}
