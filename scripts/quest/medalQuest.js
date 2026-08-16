/**
 * Generic fallback for medal quests - the 39 quests that carry a "viewMedalItem" in
 * Quest.wz/QuestInfo.img, declare a start or end script in Check.img, and ship no per-quest
 * .js of their own. QuestScriptManager.getQuestScriptEngine routes them here.
 *
 * Nothing in this file decides whether the medal is deserved, and it never did.
 * QuestActionHandler has already run Quest.canStart / Quest.canComplete by the time the
 * engine loads, so Check.img's own gate has passed: 24 of the 39 require the medal item
 * itself (29910 Gallant Warrior requires 1142009, which is the medal), 9 require a finished
 * prerequisite chain (29904 Noblesse requires quest 20000 completed), the rest add job and
 * level. Act.img declares no actions for any of the 39, so flipping the quest status is the
 * whole of the flow - there is no reward to hand out, and Quest.forceComplete already sends
 * the completion effect. The old apology this fallback printed at the player - that the medal
 * was uncoded - was therefore wrong twice: it announced a working path as broken, and it was
 * the only thing in this file that actually was broken.
 *
 * What Check.img does encode and the old fallback ignored: eight of these quests declare
 * BOTH a startscript and an endscript - 29002, 29400, 29500, 29501, 29502, 29503, 29505,
 * 29506, the Title Challenges from Dalair and Spiegelmann. Two scripts means two visits to
 * the NPC: accept the challenge, then come back and claim it. 29501's own quest text says so
 * outright ("if I hunt a Horned Tail and don't report it to him, it won't count").
 * Completing inside start() collapsed both halves into a single click.
 *
 * Known gap, deliberately not invented: what those eight challenges actually measure - a
 * million monsters in 30 days, 1000 fame, the server-wide Horned Tail kill leaderboard - is
 * not in Quest.wz at all and is not modelled anywhere on this server. Nexon's own server
 * script held it. The two-visit shape below is what the data proves; the counter is not.
 */

function start(mode, type, selection) {
    const Quest = Java.type('server.quest.Quest');

    qm.forceStartQuest();
    if (!Quest.getInstance(qm.getQuest()).hasScriptRequirement(true)) {
        award();
    }
    qm.dispose();
}

function end(mode, type, selection) {
    award();
    qm.dispose();
}

function award() {
    qm.forceCompleteQuest();
    qm.earnTitle("<" + qm.getMedalName() + "> has been awarded.");
}
