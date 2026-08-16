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

// Maps 240070000 (Tera Forest Time Gate) and 240070100 .. 240070600 (the Neo City eras).
// No Direction node named "TD_NC_title" exists - this is the theme-dungeon area title plate.
// Map.wz/Effect.img holds temaD/enter/teraForest and temaD/enter/neoCity1 .. neoCity6, and the
// hundreds digit of the map id selects between them: 240070000 -> 0 -> teraForest,
// 240070100 -> 1 -> neoCity1, and so on up to 240070600 -> 6.
function start(ms) {
    var era = Math.floor(ms.getMapId() / 100) % 10;

    if (era == 0) {
        ms.mapEffect("temaD/enter/teraForest");
    } else if (era >= 1 && era <= 6) {
        ms.mapEffect("temaD/enter/neoCity" + era);
    }
}
