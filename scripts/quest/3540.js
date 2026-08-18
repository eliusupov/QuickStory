/* Quest 3540 - "In Search of Lost Memories" (v84, ticket 09).
 * EVAN only: Check.img/3540/0/job is 2200-2218 plus 2001, i.e. EVAN1..EVAN10 and the Evan
 * beginner (Job.java:59-63) - NOT Aran (2100-2112). Evan is playable since ticket 13, so this is
 * live content now; the remaining gate is ordinary: Check.img/3540/0 wants quest 3507 STARTED,
 * and 3507 wants Lv.103 plus quest 3506 completed (the Leafre chain at NPC 2140001). No job
 * restriction sits on 3506/3507, so an Evan walks that chain like anyone else.
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
