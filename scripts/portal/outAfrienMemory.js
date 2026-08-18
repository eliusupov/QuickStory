// Portal "out00" of 900030000 ("Afrien's Memory" / "Behind the Stronghold").
// Effect.wz has no Direction node named outAfrienMemory, and the portal's own tm is
// 999999999, so the script has to supply the destination or the portal is dead - a portal
// with a script name never falls back to tm/tn (GenericPortal.enterPortal).
// String.wz/ToolTipHelp.img/PortalTooltip/900030000/out00 calls the far side the "Black
// Magician Expedition Force Stronghold", but no such map exists in this WZ, so fall back on
// the map's own returnMap/forcedReturn, 914100021.
//
// It is also the writer of quest record 22601 = "1", the COMPLETE gate of quest 22591 "The Past,
// Onyx Dragons, Black Mage" (Check.img/22591/1: infoNumber 22601, infoex/0/value "1").
//
// That the write belongs INSIDE the memory rather than on the accept is stated by
// Say.img/22591/1/stop/default/0, the line shown while 22601 is not yet "1": "If you desire to see
// the past again, #bforfeit the quest#k and speak with me" - i.e. you are sent into the past by
// accepting (Check.img/22591/0/startscript = q22591s), so the gate can only be satisfied by
// something that happens after you get there. QuestInfo.img/22591/1 "#bInside #p1205000#'s memory,
// become Freud#k" identifies this map: 900030000 is the only map in this tree with a user node
// forcing the player's look (user/0 and user/1, by gender), and its returnMap/forcedReturn is
// 914100021, Afrien's cave.
//
// And this portal is the only server-reachable hook the memory has. 900030000's info/onUserEnter
// and info/onFirstUserEnter are both the EMPTY STRING, its reactor node is empty, and its portal
// list is two entries - sp and this one. Its one NPC, 1013205, carries
// Npc.wz/1013205.img/info/script/0/script = "Afirentalk" and has no server script, and 22591's own
// completion NPC is present-day Afrien 1205000 (Check.img/22591/1/npc), so no scripts/npc/1013205.js
// is owed. Note the reason is "nothing in the quest data needs him", NOT "info/script makes an NPC
// unreachable" - that inference is false and was corrected in ticket 55 R46: 1200004, 1100008 and
// 1013207 all carry an info/script leaf and all three are server-talkable.
//
// The guard picks the record: 22601 sits in the COMPLETE block, so Quest.getInfoNumber only
// resolves it for a STARTED 22591. The player lands one map from the npc that consumes it.
function enter(pi) {
    if (pi.getQuestStatus(22591) == 1) {
        pi.setQuestProgress(22591, 22601, 1);
    }

    pi.playPortalSound();
    pi.warp(914100021, 0);
    return true;
}