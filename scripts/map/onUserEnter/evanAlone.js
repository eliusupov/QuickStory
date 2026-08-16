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

// Maps 900010000 (Dream Forest Entrance) and 900020100 (Henesys farm).
// Effect.wz has no Direction node named "evanAlone" - these are walk-around maps whose
// content comes from their scr* trigger portals (evantalk*, mirtalk*, evanFall), so all
// that is owed on entry is releasing the UI lock the preceding cutscene left behind,
// exactly like evanleaveD.js does for 900010200 / 900020200.
function start(ms) {
    ms.unlockUI();
}