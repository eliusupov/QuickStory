/*
 * Evan story quest 22541. v84 Check.img/22541: npc 1032001, lvmin 30, startscript q22541s
 * and no endscript - so only the accept side is server scripted. Completion runs Cosmic's
 * normal quest path and Act.img/22541 pays the rewards; nothing is owed here.
 *
 * ponytail: one screen, not an invented conversation. Say.img/22541/0 is empty in v84 - GMS
 * kept this dialogue in its own server script - so the original text is not in the wz and
 * cannot be recovered. The line below is built from the quest's real completion requirement
 * instead, which is the part a player actually needs.
 */
function start(mode, type, selection) {
    qm.forceStartQuest();
    qm.sendOk("Talk to #p1052106# to continue.");
    qm.dispose();
}