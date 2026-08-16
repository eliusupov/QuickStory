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

// Map 900090104. Effect/Direction4.img/incubation exists but is an empty node - it has no
// Scene children in this WZ, so there is nothing to showIntro and no client-side type-2
// warp to end the scene on. 900090104's own returnMap/forcedReturn says the chain lands on
// 100030300, so send the player there instead of leaving them on a black dead-end map.
function start(ms) {
    ms.unlockUI();
    ms.warp(100030300, 0);
}