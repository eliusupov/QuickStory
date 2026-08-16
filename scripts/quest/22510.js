/*
 * Evan story quest 22510 - deliver Gustav's letter. v84 Check.img/22510: npc 1013103,
 * lvmin 13, startscript q22510s; completion at npc 1012003 requires item 4032455 x1.
 * Act.img/22510/0 is empty, so the letter has to be handed over here or the quest is
 * impossible to finish. Act.img/22510/1 removes it and pays exp 1500 + 1 sp.
 */
function start(mode, type, selection) {
    if (!qm.canHold(4032455, 1)) {
        qm.sendOk("Make some room in your #bEtc#k inventory first, then talk to me again.");
        qm.dispose();
        return;
    }
    qm.gainItem(4032455, 1);
    qm.forceStartQuest();
    qm.sendOk("Take the letter to #p1012003# in #m100000000#.");
    qm.dispose();
}