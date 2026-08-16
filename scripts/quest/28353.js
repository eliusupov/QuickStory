/* Quest 28353 - "The Shadow Knight in Dragon's Nest" (v84, ticket 09).
 * Start at the Maple Administrator (9010000), Lv.15+; complete at Shadow Knight Rene
 * (9201144) - the NPC ticket 06 placed in Dragon's Nest and ticket 03f renamed.
 * Only the start half is scripted. The completion half is data-driven and Act.img/28353/1
 * gives exp 2000, so this script deliberately hands out nothing.
 * Check.img/28353/0/end is "2010042100" - the event window has closed.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    qm.dispose();
}
