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

// Map 106020501 (Mushroom Castle : Castle Wall Edge). The "gasi" (thorn) cutscene of the
// Mushroom Castle theme dungeon lives in Effect/Direction2.img/gasi and is fired by the
// TD_MC_gasi hook on 106020502; there is no Direction node named "TD_MC_gasi2", so the "2"
// hook is the tail of that sequence and only has to hand the UI back to the player.
function start(ms) {
    ms.unlockUI();
}
