// The ten scr00..scr09 triggers of 914100020 (Slumbering Dragon Island : Cave of Silence, the
// ice-wall room). Map/Map9/914100020.img/portal/2..11 are all pt=9, delay=200,
// script="stopIceWall" - one script bound to ten portals, which is why this file is named after
// the script and not after a map or an index.
//
// Writer of quest record 22599 = "2", the COMPLETE gate of quest 22580 (Check.img/22580/1:
// infoNumber 22599, infoex/0/value "2").
//
// The instruction is "head toward the center of the Island" - QuestInfo.img/22580/1 - and
// Say.img/22580/1/stop/default/0, which is the line the client shows while 22599 is not yet "2",
// repeats it: "Hurry, master! Let's go to the #bcenter of the island#k!". The geometry says these
// triggers ARE the centre: 914100020's info gives VRLeft=-636 and VRRight=723, so the horizontal
// middle is x~43, and the ten triggers sit at x=56 and x=153. The player arrives at the far left
// edge (portal/0 x=-465, portal/1 x=-548). Walking to the centre of this room is touching one of
// these.
//
// Nothing else in the room can carry the write: 914100020's info/onUserEnter and
// info/onFirstUserEnter are both the EMPTY STRING, its reactor node is empty and its life is empty.
// And v84 gave this grid a different script name from the identical grid in 914100022
// ("stopIceWall2"), which only earns its own name if one of the two carries state the other does
// not.
//
// The guard picks the record. Character.setQuestProgress resolves the slot through
// Quest.getInfoNumber(status), and only a STARTED 22580 resolves 22599 off the COMPLETE block;
// firing this while 22580 is NOT_STARTED would write into the start gate instead.
//
// ponytail: the record write only, and no warp - pt=9 is a trigger, not a door, and the room's way
// out is portal/1 out00. Actually stopping the ice wall is mob 9300391's encounter, which is
// script-spawned (placed in no map) and whose count and positions are in no WZ file, so it is not
// invented here. Effect.wz has no Direction node named stopIceWall: never play a cutscene here.
function enter(pi) {
    if (pi.getQuestStatus(22580) == 1) {
        pi.setQuestProgress(22580, 22599, 2);
    }

    return true;
}
