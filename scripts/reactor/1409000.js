/* Ice Wall altar (reactor 1409000), Cave of Silence 914100022.
    Quest 22588 "Secret Organization's Fifth Mission".

    Every link here is stated by v84, not inferred:
      Act.img/22588/0/item/0/id      Hiver hands you 4032473 when you accept
      QuestInfo.img/22588/1          "offer it on an altar inside a cave in the centre of the
                                     island ... You just have to drop the 4032473 on the altar"
      Map.wz/Map9/914100022/reactor/0  id 1409000 at (-243, 6), the only placement in Map.wz
      Reactor.wz/1409000/info/info   "break down the ice wall" (Korean)
      Reactor.wz/1409000/0/event/0   type 100 (drop-item), item 4032473 qty 1, -> state 1
      Check.img/22588/1              infoNumber 22605, infoex/0/value "1"

    Type 100 is the server's drop-item reactor: MapleMap.searchItemReactors matches the item in
    the reactor's lt/rb box, ActivateItemReactor consumes it and hits the reactor, and state 1 is
    the end state, so this act() is what runs.

    ponytail: the record write only. Breaking the wall itself belongs to the missing
    summonIceWall / stopIceWall scripts - mob 9300391 "Ice Wall" is placed in NO map, so it is
    script-spawned and the v84 data does not state how many or where. Writing a "the wall
    crumbles" message here would describe something that is not on screen.
 */

function act() {
    if (rm.isQuestStarted(22588)) {
        rm.setQuestProgress(22588, 22605, 1);
    }
}
