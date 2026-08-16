/* Quest 2344 - "Mushking Empire in Danger" (v84, ticket 09).
 * Aran only (job 2210-2218), Lv.30-38. Start at Manji (1040001), complete at 1300005
 * after handing over 1 #t4032375#.
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
