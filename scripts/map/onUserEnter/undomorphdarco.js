/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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

// Map 240000110 (Leafre : Station) - the Leafre end of the dragon flight to the Temple of Time.
// No Direction node named "undomorphdarco" exists; the name is literal, it undoes the dragon
// morph. 2210016 is the morph this server uses for that flight: scripts/npc/2082003.js applies
// it with useItem(2210016), scripts/portal/undodraco.js cancels it on the way in and
// scripts/portal/templeenter.js cancels it at the far end. Doing it in the map hook as well is
// what keeps a player who arrives by any other route (relog, forcedReturn) from being stuck as
// a dragon. cancelEffect on an inactive buff is a no-op.
function start(ms) {
    ms.cancelItem(2210016);
}
