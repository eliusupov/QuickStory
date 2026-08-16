/* Quest 10491 - Evan launch event (Korean name in v84). Completed at NPC 9000021 while
 * quest 10490 is started, carrying 1 #t3994185#.
 * Act.img/10491 is empty on both sides - no exp, item or meso, and the WZ does not consume
 * the #t3994185#, so this script does not either.
 */
function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceCompleteQuest();
    qm.dispose();
}
