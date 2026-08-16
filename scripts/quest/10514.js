/* Quest 10514 - "Evan Launch Commemoration 2PM Event" (v84, ticket 09).
 * Cassandra (9010010), Lv.13+, one hour window on 2009-12-26 (Check.img/10514/0/{start,end}).
 * The window is long past, so Cosmic's EndDateRequirement refuses the start.
 * Act.img/10514 is empty on both sides - GMS handed the gift out through the event system,
 * not through the quest, so this script hands out nothing.
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
