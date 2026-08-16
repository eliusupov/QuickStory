/* Quest 10497 - Evan launch event (Korean name in v84). Cassandra (9010010), requires quest
 * 10490 started and neither #t3994184# nor #t3994185# in the inventory.
 * Act.img/10497 is empty on both sides; the completion side is gated on infoex "99999",
 * an event counter nothing in this tree produces.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    qm.dispose();
}
