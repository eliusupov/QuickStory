/* Quest 10510 - "Evan Everyday Event" (v84, ticket 09). Cassandra (9010010), Lv.13+,
 * repeatable every 60 min, autoComplete.
 * Start requires the player to hold neither #t3994187# nor #t3994186#; completion requires
 * 1 #t3994187#. Act.img/10510 is empty on both sides, so neither half hands anything out -
 * the event item comes from elsewhere in GMS and nothing in this tree produces it.
 * Check.img/10510/0/end is "201005050000" - the event window has closed.
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
