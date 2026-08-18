/*
 * Evan story quest 22591. v84 Check.img/22591: npc 1205000, lvmin 70, startscript q22591s
 * and no endscript - so only the accept side is server scripted. Completion runs Cosmic's
 * normal quest path and Act.img/22591 pays the rewards; nothing is owed here.
 *
 * ponytail: one screen, not an invented conversation. Say.img/22591/0 is empty in v84 - GMS
 * kept this dialogue in its own server script - so the original text is not in the wz and
 * cannot be recovered. The line below is built from the quest's real completion requirement
 * instead, which is the part a player actually needs.
 */
function start(mode, type, selection) {
    qm.forceStartQuest();
    qm.sendOk("Talk to #p1205000# to continue.");
    // v84 accept-time warp: map 900030000 (Afrien's Memory) has no field-side entrance;
    // this start script is the entrance. out00 (outAfrienMemory) writes record 22601=1 on exit.
    qm.warp(900030000, 0);
    qm.dispose();
}