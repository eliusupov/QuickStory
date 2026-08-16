/* Quest 10516 - "Evan Launch Commemoration 2PM Event" (v84, ticket 09), the 2010-01-02 rerun
 * of 10514. Cassandra (9010010), Lv.13+, one hour window (Check.img/10516/0/{start,end}).
 * The window is long past. Act.img/10516 is empty on both sides.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    qm.sendOk("You weren't able to receive the gift because you had no space left in your inventory. You're not too late. Here is your gift!");
    qm.forceStartQuest();
    qm.dispose();
}
