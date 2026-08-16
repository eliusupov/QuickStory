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

// Maps 100030102 (Utah's House : Front Yard) and 914100021 (Slumbering Dragon Island : Cave of
// Silence) - the two maps Evan walks with Mir.
// Effect.wz has no Direction node named "evanTogether" (checked Direction.img through
// Direction4.img), so this is not a cutscene. Both maps are plain walk-around maps whose content
// hangs off their scr* trigger portals (evanGarden0/evanGarden1 on 100030102) or a placed NPC
// (Afrien, 1205000, on 914100021), so entry only owes the UI unlock - same as evanAlone.js.
function start(ms) {
    ms.unlockUI();
}
