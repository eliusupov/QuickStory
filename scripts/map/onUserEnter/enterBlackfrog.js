/*
    The Frog House fight room, 922030001. Quest 22596 "Rage".

    The file name is v84's spelling, not a choice: Map/Map9/922030001.img/info/onUserEnter is the
    string "enterBlackfrog" with a lowercase f, and MapScriptManager loads
    "map/" + "onUserEnter/" + <that string> + ".js". It is deliberately a DIFFERENT file from
    scripts/portal/enterBlackFrog.js (capital F), which is the door on 220000300 that routes here.

    Every link is stated by v84:

      QuestInfo.img/22596/1        "go to the #b#m922030001##k in #m220000300# where you met
                                   #p1013203# in person ... #o9300393#"
      Say.img/22596/1/stop/mob/0   "let's hurry to the #b#m922030001##k ... #r#p1013203##k is
                                   probably still there!"
      Check.img/22596/1/mob/0      id 9300393, count 1 - a hard kill, no alternative
      Map9/922030001.img/life      EMPTY, and mob 9300393 appears in no map's life in all of
                                   pristine v84 (0 hits / 4848 images), and Mob.wz/9300393 has no
                                   info/revive, so nothing reaches it by a revive chain either.
                                   Mob.wz/9300393/info/summonType=1 - it is summoned, by this.
      Map9/922030001.img/info/onUserEnter  "enterBlackfrog"

    So v84 declares that a hook by this name runs on entry, and the only thing the room can need is
    the one mob the quest demands. Mob 9300393's name string is "Gentleman"; it is Hiver in the
    fight - Item.wz/Consume/0210.img/02100165/mob/0/id is 9300393 and String.wz/Consume.img/2100165
    is "Hiver Summoning Sack" / "Summons Hiver".

    The position is sourced, not invented. 922030001 is 922030000 with the platforms stripped:
    footholds 1/2/3 are byte-identical in both (the shell, with ONE continuous floor at y=31 from
    x=-310 to x=314), and 922030001 keeps only the entry ledge. Hiver stands in the twin room at
    Map9/922030000.img/life/0 x=-221, so that is the x used here; the platform he stands on there
    (foothold 5, y=-174) does not exist here, which is why the y is left to
    spawnMonsterOnGroundBelow and the room's single floor. Any x inside the corridor resolves to
    that same floor - there is no second surface to land on by mistake.

    ponytail: idempotence guard only, no quest gate. The room is reachable solely through
    enterBlackFrog.js, which routes here only while 22596 is started, so a gate here would be a
    second lock on the same door - and onUserEnter fires on EVERY entry, so the guard that actually
    matters is "do not stack a second Hiver". Same shape as 108010101.js.
*/
function start(ms) {
    const LifeFactory = Java.type('server.life.LifeFactory');
    const Point = Java.type('java.awt.Point');

    var map = ms.getPlayer().getMap();
    if (map.getMonsterById(9300393) != null) {
        return;
    }

    map.spawnMonsterOnGroundBelow(LifeFactory.getMonster(9300393), new Point(-221, 0));
}
