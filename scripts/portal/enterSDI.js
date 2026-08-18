// Portal "tel00" of 922030000 (Frog House) - the single remaining gate on Slumbering Dragon
// Island. Map.wz, ours and pristine v84 byte-identical on this node:
//   Map/Map9/922030000.img/portal/2  pn=tel00 pt=8 x=-205 y=32 tm=999999999 tn=""
//                                    horizontalImpact=0 script=enterSDI
// pt=8 is neither the scripted warp (7) nor the touch trigger (9) the other Evan doors use, and
// needs no new mechanism: PortalFactory.java:37-42 makes every type except MAP_PORTAL(2) a
// GenericPortal, and GenericPortal.java:130-142 runs the script for any type that names one.
//
// One destination, and it is not a guess. tm=999999999 means the server picks; a tm scan over all
// 4848 pristine v84 map images returns ZERO hits for 922030000, 200090080 and 200090090, i.e. no
// portal anywhere in the game supplies the island, so this script is the only way in and the
// island's five Evan quests (22580, 22588, 22589, 22590, 22591) are behind it.
//
// 914100000 "Slumbering Dragon Island" is the landing:
//   - it is the island's only info/town=1 map, and its own returnMap is itself;
//   - it is what the ferry declares: 200090090/portal/1..8 (hd00..hd07) are all pt=3 tm=914100000;
//   - its portal/1 in00 is a plain pt=2 into 914100010/west00, which is where onSDI.js writes
//     quest record 22599="1", the START gate of 22580. Landing anywhere deeper skips that write.
// Landing on portal 0 ("sp"), not on a name: 914100000 has exactly two portals, sp and in00, and
// the ferry's own tn="st00" names a portal that does not exist on this map - GenericPortal.java:
// 148-150 falls back to getPortal(0) for it. Ask for slot 0 directly rather than copy a dangling
// reference.
//
// The route in front of this door is already live: 220000300/portal/4 pn=scr00 pt=7
// script=enterBlackFrog lands at 922030000, so El Nath -> Frog House -> here -> island.
//
// Effect.wz has no Direction node named enterSDI, so this is not a cutscene and must never play
// one - a scene path the client cannot resolve takes the client down.
function enter(pi) {
    pi.playPortalSound();
    pi.warp(914100000, 0);
    return true;
}
