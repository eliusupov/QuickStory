/* Quest 10493 - Evan launch event (Korean name in v84). Completed at NPC 2001000 while
 * quest 10490 is started, carrying 1 #t3994185#.
 * Act.img/10493 is empty on both sides.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceCompleteQuest();
    qm.dispose();
}
