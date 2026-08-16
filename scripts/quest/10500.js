/* Quest 10500 - "A Sign of the Dragon Master's Return" (v84, ticket 09).
 * Cassandra (9010010), Lv.13+. Act.img/10500 is empty on both sides.
 * Check.img/10500/0/end is "200912170000" - the event window has closed.
 */
var status = -1;

function start(mode, type, selection) {
    if (mode == -1) {
        qm.dispose();
        return;
    }
    if (mode == 0) {
        qm.sendOk("I called you so that I could give you info about the #bDragon Master#k and give you a gift as a bonus but you don't want it? You're going to regret it...");
        qm.dispose();
        return;
    }
    status++;

    if (status == 0) {
        qm.sendYesNo("Have you heard about the #bDragon Master#k? No? Hehe, that's what I figured. If you're curious about #bDragon Master Evan#k, come talk to me.");
    } else if (status == 1) {
        qm.forceStartQuest();
        qm.sendOk("You do know where I am right? I can be found in any of the major towns in Maple World.");
        qm.dispose();
    }
}
