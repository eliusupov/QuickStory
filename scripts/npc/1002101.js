// Olaf, Lith Harbour (104000000) - the Slumbering Dragon Island ferry's boarding NPC.
//
// v84 places 1002101 in exactly one map: Map.wz/Map/Map1/104000000.img/life/11 (WzPeek scan over
// all 4,848 pristine map images returns that one hit). He carries
// Npc.wz/1002101.img/info/script/0/script = "contimoveSDIRit" in the pristine carve, and
// Etc.wz/ScriptInfo.img/contimoveSDIRit = "Ask about boarding the ship." is that leaf's tooltip -
// the only contimove entry in the whole of ScriptInfo.img.
//
// An info/script leaf does NOT stop a server conversation. The two ferry NPCs this server already
// drives in production carry the identical node: 1200004 has "contimoveRitRie" and 1100008 has
// "contimoveOrbEre", in the pristine carve AND in wz/Npc.wz today, and scripts/npc/1200004.js /
// 1100008.js work. This file is the same mechanism for the same kind of NPC.
//
// Destination 200090080 is the Lith -> SDI ride map, not a guess: its own hd00..hd07 warp back to
// 104000000 and its returnMap/forcedReturn are 104000000, so Lith is the ORIGIN - exactly the
// shape 200090020 (FROM_ORBIS_TO_EREVE) has. Its out00..out05 name "move_RitSDI", Rit -> SDI.
// MapleMap.addPlayer schedules the landing at 914100000; nothing here warps to the island.
//
// No fare. 1200003/1200004 charge 800 mesos, but v84 states no price for THIS route anywhere -
// ScriptInfo has one sentence and no numbers - and inventing one is exactly what the evidence
// rules forbid. If the owner wants a fare it is one gainMeso line.
var status;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode != 1) {
        cm.dispose();
        return;
    }
    status++;
    if (status == 0) {
        cm.sendSimple("Ask about boarding the ship? She sails for the frozen island up north."
                + "\r\n#L0##bSlumbering Dragon Island#k#l");
    } else {
        cm.warp(200090080);
        cm.dispose();
    }
}
