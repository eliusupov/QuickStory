/*
 * Evan story quest 22603. v84 Check.img/22603: npc 1013000, lvmin 120, startscript q22603s
 * and no endscript - so only the accept side is server scripted. Completion runs Cosmic's
 * normal quest path and Act.img/22603 pays the rewards; nothing is owed here.
 *
 * ponytail: one screen, not an invented conversation. Say.img/22603/0 is empty in v84 - GMS
 * kept this dialogue in its own server script - so the original text is not in the wz and
 * cannot be recovered. The line below is built from the quest's real completion requirement
 * instead, which is the part a player actually needs.
 *
 * The scale is the quest. QuestInfo.img/22603/1 - v84's own in-progress text, read whole - says
 * the item is already handed over while the quest runs: "#p1013000# gives you another scale that
 * contains the power of the Dragon. It seems to contain stronger power than the last time. You
 * should be able to make another good item using the #bMake Skill#k." Act.img/22603 is empty, so
 * Mir's own script is the only place it can come from.
 *
 * Which scale is not a guess: makercreatedata (158-maker-v84-data.sql:74, read straight out of
 * v84's Etc.wz/ItemMake.img/0/01142157) makes medal 1142157 from 4032503 "Shiny Dragon Scale" at
 * reqLevel 120, and this quest's own Check.img lvmin is 120. 22602 is the 80 pair with 4032502.
 * Without this the medal quest 29939 "Dragon Master" can never start: Check.img/29939/0 requires
 * the medal itself, the medal requires the scale, and nothing else in the tree drops or gives it.
 */
function start(mode, type, selection) {
    if (!qm.canHold(4032503, 1)) {
        qm.sendOk("Please have a slot available in your Etc inventory.");
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    if (!qm.haveItem(4032503, 1)) {
        qm.gainItem(4032503, 1);
    }
    qm.sendOk("Talk to #p1013000# to continue.");
    qm.dispose();
}