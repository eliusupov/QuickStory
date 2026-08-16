/* Quest 3540 - "In Search of Lost Memories" (v84, ticket 09).
 * Aran (job 2200-2218) and Evan beginners (2001). Requires quest 3507 already started.
 * Start and complete both at NPC 1012003.
 * Only the start half is scripted in Quest.wz (Check.img/3540/0/startscript); the complete
 * half is data-driven. Act.img/3540 is empty on both sides, so there is no reward to hand out.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    qm.dispose();
}
