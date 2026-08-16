/* Quest 28361 - "First Evan Launch Gift from the Admin" (v84, ticket 09).
 * Maple Administrator (9010000), Lv.17+.
 * Act.img/28361 is empty on both sides, but QuestInfo.img/28361/rewardSummary declares
 * "#i1702268:# #t1702268:# 1" - so GMS's own end script is what handed the item over.
 * This one does exactly that and nothing else.
 * Check.img/28361/0/end is "2010050500" - the event window has closed.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    if (!qm.canHold(1702268)) {
        qm.sendOk("Please make some room in your equip inventory first.");
        qm.dispose();
        return;
    }
    qm.gainItem(1702268, 1);
    qm.forceCompleteQuest();
    qm.dispose();
}
