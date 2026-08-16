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

// Map 2000000 (Maple Road : Southperry). Map.wz/Effect.img has maplemap/enter/2000000, so this
// is the map-name plate, identical in shape to go1000000.js.
// No unlockUI here on purpose: 2000000 is only ever walked into (from 1020000/west00 or
// 2000001/out00), never handed over from a cutscene, unlike go10000/go20000.
function start(ms) {
    ms.mapEffect("maplemap/enter/2000000");
}
