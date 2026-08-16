/* Quest 10481 - "The Maple Administrator's Congratulations" (v84, ticket 09).
 * Maple Administrator (9010000), Lv.20+. Act.img/10481 is empty on both sides.
 * Check.img/10481/0/end is "201005050000" - the event window has closed.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    qm.dispose();
}
