/*
 * Evan story quest 22585. v84 Check.img/22585: npc 1013000, lvmin 68, startscript q22585s
 * and no endscript - so only the accept side is server scripted. Completion runs Cosmic's
 * normal quest path and Act.img/22585 pays the rewards; nothing is owed here.
 *
 * ponytail: one screen, not an invented conversation. Say.img/22585/0 is empty in v84 - GMS
 * kept this dialogue in its own server script - so the original text is not in the wz and
 * cannot be recovered. The line below is built from the quest's real completion requirement
 * instead, which is the part a player actually needs.
 */
function start(mode, type, selection) {
    qm.forceStartQuest();
    qm.sendOk("Talk to #p1013000# to continue.");
    qm.dispose();
}