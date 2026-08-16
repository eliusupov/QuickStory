/* Quest 10492 - Evan launch event (Korean name in v84). Completed at the Maple Administrator
 * (9010000) while quest 10490 is started, carrying 1 #t3994185#.
 * Act.img/10492 is empty on both sides.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceCompleteQuest();
    qm.dispose();
}
