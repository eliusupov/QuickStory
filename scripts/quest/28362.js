/* Quest 28362 - "Second Evan Launch Gift from the Admin" (v84, ticket 09).
 * Maple Administrator (9010000), Lv.18+.
 * Act.img/28362 is empty; QuestInfo.img/28362/rewardSummary declares two items and picks by
 * gender - "For Male Evan #i1050168#, For Female Evan #i1051209#". Gender 0 is male in
 * Cosmic (see scripts/event/WeddingCathedral.js:133).
 * Check.img/28362/0/end is "2010050500" - the event window has closed.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    var gift = qm.getPlayer().getGender() == 0 ? 1050168 : 1051209;
    if (!qm.canHold(gift)) {
        qm.sendOk("Please make some room in your equip inventory first.");
        qm.dispose();
        return;
    }
    qm.gainItem(gift, 1);
    qm.forceCompleteQuest();
    qm.dispose();
}
