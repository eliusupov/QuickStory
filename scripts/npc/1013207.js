// Olaf, the Slumbering Dragon Island end of the ferry - and the deckhand on both ride maps.
//
// v84 places 1013207 in exactly three maps (WzPeek scan id=1013207 over all 4,848 pristine map
// images): 914100000 (the island's Temporary Harbor, its only town=1 map), 200090080 and
// 200090090 (the two ride maps). He carries Npc.wz/1013207.img/info/script/0/script =
// "contimoveRitSDI" in pristine AND in wz/Npc.wz today. The direction suffixes are crossed
// relative to the portal scripts - the Lith-side NPC 1002101 carries SDIRit - so the map the
// click comes from decides what happens, not the name.
//
// The map guard is load-bearing, not cosmetic. He stands ON the ships, and boarding from a ship
// is the ride-skip ticket 37 refuses: a passenger halfway to the island could re-board and land
// instantly. Aboard, MapleMap.addPlayer has already scheduled the landing and there is nothing
// for him to do.
//
// Destination 200090090 is the SDI -> Lith ride map: its hd00..hd07 warp back to 914100000, so
// the island is the ORIGIN, and its out00..out05 name "move_SDIRit". MapleMap.addPlayer lands the
// passenger at 104000000 portal 3, the slot FROM_RIEN_TO_LITH already uses for a ship docking at
// Lith. No fare, for the reason recorded in scripts/npc/1002101.js.
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
    if (cm.getMapId() != 914100000) {
        cm.sendOk("Sit tight. We'll be pulling in before long.");
        cm.dispose();
        return;
    }
    if (status == 0) {
        cm.sendSimple("Ask about boarding the ship? She sails back to Victoria Island."
                + "\r\n#L0##bLith Harbor#k#l");
    } else {
        cm.warp(200090090);
        cm.dispose();
    }
}
