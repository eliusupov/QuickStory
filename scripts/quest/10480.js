/* Quest 10480 - "The Birth of a New Hero" (v84, ticket 09).
 * Maple Administrator (9010000), Lv.20+, Evan launch event.
 * Act.img/10480 is empty on both sides - the quest is a notice, not a reward.
 * Check.img/10480/0/end is "201005050000", so the event window has already closed:
 * Cosmic's EndDateRequirement refuses the start until an owner drops that node.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    qm.dispose();
}
