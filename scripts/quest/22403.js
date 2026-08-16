/*
 * Evan story quest 22403. v84 Check.img/22403: npc 1101002, lvmin 50, startscript q22403s
 * and no endscript - so only the accept side is server scripted. Completion runs Cosmic's
 * normal quest path and Act.img/22403 pays the rewards; nothing is owed here.
 *
 * ponytail: one screen. v84 does keep a six-page #L0# branching dialogue in Say.img/22403/0,
 * but Quest.java reads QuestInfo/Act/Check only and never opens Say.img, so replaying it here
 * would mean hand-porting a menu tree for pure flavour. The line below is built from the
 * quest's real completion requirement instead - the part a player actually needs.
 */
function start(mode, type, selection) {
    qm.forceStartQuest();
    qm.sendOk("Talk to #p2060005# to continue.");
    qm.dispose();
}