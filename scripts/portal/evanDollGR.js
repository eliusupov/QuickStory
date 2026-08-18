/*
    Ticket 08 (GMS v84). Golem's Temple 2 (106010102) -> 910600010 Abandoned Hideout.

    Client half is Map.wz/Map/Map1/106010102.img/portal/8 (pn=scr00, pt=8), a v84 addition this
    ticket merges as a verified pure append onto the live 8-portal array.
    910600010's own out00 returns to 106010102 at tn=scr00, i.e. this portal.

    Ungated on purpose - see enterDollcave.js.

    ------------------------------------------------------------------------------------------
    This is also the writer of quest record 22598, which gates 22556 and 22557. Every link is
    stated by v84:

      QuestInfo.img/22556/showLayerTag  "22556"
      Map1/106010102.img/3/obj/13/tags  "22556"  - a guide/tutorial/key sprite (the client's
                                        "press UP here" prompt) at x=1458 y=193, shown ONLY while
                                        22556 is in progress. This portal is at x=1453 y=252.
                                        Both are v84 additions (add-list/Map.txt:246, :297).
      Say.img/22556/0/yes/2             "go no further than #b#m106010102##k and see if you can
                                        find out anything"
      Say.img/22556/1/0                 the report Stan asks for is "There was a door with a
                                        strange puppet sitting on top." - this portal is that
                                        door; obj 6-12, also v84 additions, are its golem scenery.
      Check.img/22556/1                 infoNumber 22598, infoex/0/value "1"

      Say.img/22557/0/0                 "One of the Golems grabbed #p1012108# and disappeared into
                                        the Golem's Temple!"
      Say.img/22557/1/stop/default/0    "Go into the #bGolem's Temple#k and rescue #b#p1012108##k!"
      Check.img/22557/1                 infoNumber 22598, infoex/0/value "2"

    Why BOTH writes sit on this one portal rather than on a map arrival: v84 declares no map hook
    anywhere on this path - 910600010.img/info/onUserEnter and onFirstUserEnter are the EMPTY
    STRING (a positive "no script"), and 106010102.img/info carries no such node at all. The whole
    Golem's Temple area declares exactly two script hooks in Map.wz, this one and evanGolemDoor on
    106010101/portal/5. One record slot carrying two values is one hook hit twice, which is also
    why v84 spends 22598 on both quests instead of a slot each (contrast 22589, which uses 22600
    and 22604 for its two distinct triggers).

    The two quests cannot both be STARTED - Check.img/22557/0/quest/0 requires 22556 at state 2 -
    so the two branches are mutually exclusive by construction.
*/
function enter(pi) {
    if (pi.isQuestStarted(22556)) {
        pi.setQuestProgress(22556, 22598, 1);
    } else if (pi.isQuestStarted(22557)) {
        pi.setQuestProgress(22557, 22598, 2);
    }

    pi.playPortalSound();
    pi.warp(910600010, "out00");
    return true;
}
