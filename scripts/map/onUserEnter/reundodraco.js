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

// Map 270000100 (Time Lane : Temple of Time) - the far end of the Leafre dragon flight.
// This is the map hook, not the same-named portal script (scripts/portal/reundodraco.js is the
// arrival trigger on 240000110 and only blocks itself). No Direction node named "reundodraco"
// exists; the flight morph 2210016 is applied by scripts/portal/outTemple.js on the way out of
// this map and cancelled by scripts/portal/templeenter.js on the way in, so cancelling it on
// entry is the same guarantee undomorphdarco.js gives the Leafre side.
function start(ms) {
    ms.cancelItem(2210016);
}
