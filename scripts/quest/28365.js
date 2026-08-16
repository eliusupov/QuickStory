/* Quest 28365 - "Fifth Evan Launch Gift from the Admin" (v84, ticket 09).
 * Maple Administrator (9010000), Lv.21+. Act.img/28365 is empty;
 * QuestInfo.img/28365/rewardSummary declares "#i1082272:# #t1082272:# 1".
 * Check.img/28365/0/end is "2010050500" - the event window has closed.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    if (!qm.canHold(1082272)) {
        qm.sendOk("Please make some room in your equip inventory first.");
        qm.dispose();
        return;
    }
    qm.gainItem(1082272, 1);
    qm.forceCompleteQuest();
    qm.dispose();
}
