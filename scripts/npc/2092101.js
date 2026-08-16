/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/*
    2092101 - Potter (captive), map 925110000 "Pirate Treasure Vault".
    Evan quest 22408 "Obtaining the Unbreakable Porcelain" - the rescue step.

    WHY THIS FILE EXISTS. 22408 requires item 4032497 "Potter" x1
    (Quest.wz/Check.img.xml:25951-25958) and has an EMPTY start Act
    (Quest.wz/Act.img.xml:3673-3675), so the quest hands out nothing and the item must come from
    the world. It has no drop row, no reactor row and no shop row, and the item is not a thing -
    String.wz/Etc.img.xml:9417-9420 names it "Potter", "A master craftsman in Herb Town. He is as
    light as a feather", i.e. the rescued man himself, carried out.

    Everything else in that chain is already in place:
      - NPC 2092101 "Potter" (String.wz/Npc.img.xml:3703-3705) is placed on 925110000 in THIS
        tree and in stock v84 alike - one life entry, identical in both.
      - the way in exists: ticket 08 merged Map.wz/Map/Map2/251010403.img/portal/4
        (script "enterPottery", present at wz/.../251010403.img.xml:2153) and wrote
        scripts/portal/enterPottery.js, which warps Red-Nose Pirate Den 3 -> 925110000. That is
        the entrance QuestInfo.img.xml:7363 names: "You can enter from the #b#m251010403##k."
      - the follow-up quest 22409 already routes through the freed Potter as NPC 2092100
        (Check.img.xml:25963).
    The only missing link was that 2092101 had no script, so clicking him did nothing. That is
    the defect this file fixes.

    DIALOGUE PROVENANCE. Quest.wz/Say.img has no node for 22408 (or for any 224xx Evan quest), so
    there is no shipped script text to copy. The lines below are 2092101's own character's text
    taken verbatim from String.wz/Npc.img.xml:8467-8473 (the freed Herb Town Potter, 2092100):
    n0 "Argh..." and d0 "To make quality pottery, one must be in peaceful and relaxing
    surroundings. Those pirates just don't understand that." The one connecting sentence is
    written, not sourced, and is marked here rather than passed off as Nexon's.
*/

var status;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
        return;
    }

    if (mode == 0 && type > 0) {
        cm.dispose();
        return;
    }

    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    if (status == 0) {
        if (cm.isQuestStarted(22408) && !cm.haveItem(4032497)) {
            if (cm.canHold(4032497)) {
                cm.gainItem(4032497, 1);
                cm.sendOk("To make quality pottery, one must be in peaceful and relaxing surroundings. Those pirates just don't understand that. Get me out of here and I'll make #b#p2092001##k his #b#t4032477##k.");
            } else {
                cm.sendOk("Argh... make some room first, would you? I am not going anywhere in those full pockets of yours.");
            }
        } else {
            cm.sendOk("Argh...");
        }

        cm.dispose();
    }
}
