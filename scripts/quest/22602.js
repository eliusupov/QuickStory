/*
 * Evan story quest 22602. v84 Check.img/22602: npc 1013000, lvmin 80, startscript q22602s
 * and no endscript - so only the accept side is server scripted. Completion runs Cosmic's
 * normal quest path and Act.img/22602 pays the rewards; nothing is owed here.
 *
 * ponytail: one screen, not an invented conversation. Say.img/22602/0 is empty in v84 - GMS
 * kept this dialogue in its own server script - so the original text is not in the wz and
 * cannot be recovered. The line below is built from the quest's real completion requirement
 * instead, which is the part a player actually needs.
 *
 * The scale is the quest. QuestInfo.img/22602/1 - v84's own in-progress text, read whole - says
 * the reward is already in hand by the time the quest is running: "Of the scales that #p1013000#
 * shed, there seems to be one that still contains the power of the Dragon. Since #p1013000# has
 * new scales, he says he doesn't need it anymore. ... What about making something with it using
 * the #bMaker Skill#k." Act.img/22602 is empty, so Mir's own script is the only place it can come
 * from, exactly as it is for 2300-2310's recommendation letter.
 *
 * Which scale is not a guess: makercreatedata (158-maker-v84-data.sql:73, read straight out of
 * v84's Etc.wz/ItemMake.img/0/01142156) makes medal 1142156 from 4032502 "Dragon Scale" at
 * reqLevel 80, and this quest's own Check.img lvmin is 80. 22603 is the 120 pair with 4032503.
 * Without this the medal quest 29938 "Dragon Master" can never start: Check.img/29938/0 requires
 * the medal itself, the medal requires the scale, and nothing else in the tree drops or gives it.
 */
function start(mode, type, selection) {
    if (!qm.canHold(4032502, 1)) {
        qm.sendOk("Please have a slot available in your Etc inventory.");
        qm.dispose();
        return;
    }
    qm.forceStartQuest();
    if (!qm.haveItem(4032502, 1)) {
        qm.gainItem(4032502, 1);
    }
    qm.sendOk("Talk to #p1013000# to continue.");
    qm.dispose();
}