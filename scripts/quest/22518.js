/*
 * Evan story quest 22518. v84 Check.img/22518: npc 1012119, lvmin 16, startscript q22518s
 * and no endscript - so only the accept side is server scripted. Completion runs Cosmic's
 * normal quest path and Act.img/22518 pays the rewards; nothing is owed here.
 *
 * ponytail: one screen, not an invented conversation. Say.img/22518/0 is empty in v84 - GMS
 * kept this dialogue in its own server script - so the original text is not in the wz and
 * cannot be recovered. The line below is built from the quest's real completion requirement
 * instead, which is the part a player actually needs.
 */
function start(mode, type, selection) {
    qm.forceStartQuest();
    qm.sendOk("Defeat #r100#k #o9300386#, then talk to #p1012119#.");
    qm.dispose();
}