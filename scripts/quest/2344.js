/* Quest 2344 - "Mushking Empire in Danger" (v84, ticket 09).
 * EVAN only, Lv.30-38: Check.img/2344/0/job is 2210-2218, which client.Job calls
 * EVAN2..EVAN10 (Job.java:62-63) - NOT Aran, whose ids are 2100-2112. Evan is unimplemented
 * in this tree, so JobRequirement refuses every non-GM character until ticket 13 lands.
 * Start at Manji (1040001), complete at 1300005 after handing over 1 #t4032375#.
 * Quest.wz Act.img/2344 is EMPTY on both sides - v84 ships no exp, item or meso reward -
 * so these two functions only move the quest state. Nothing is invented here.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    qm.dispose();
}

function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceCompleteQuest();
    qm.dispose();
}
