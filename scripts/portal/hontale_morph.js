/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
                       Matthias Butz <matze@odinms.de>
                       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation. You may not use, modify
    or distribute this program under any other version of the
    GNU Affero General Public License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

/*
    Map 240040700 (Leafre : Cave of Life - Entrance), trigger portals cs00 .. cs05.
    All six sit along y ~ 700-712 on the cave floor, right on top of the gatekeeper NPC 2081005
    at (235, 731) - walking the floor is what opens him in GMS, which is why the map also has no
    ordinary portal into the cave. Without this the whole row is dead and the Horntail entrance
    is only reachable by clicking the NPC sprite.
    openNpc() already returns early when a conversation is open (AbstractPlayerInteraction), so
    stepping across several of these in a row cannot stack dialogues.
*/
function enter(pi) {
    pi.openNpc(2081005);
    return true;
}
