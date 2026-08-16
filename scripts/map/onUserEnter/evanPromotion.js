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

// Maps 900090000 - 900090004, the "Video / Teaser" cutscene chain.
// Scene-to-map order is read off Effect/Direction4.img/promotion: every Scene ends in a
// type-2 node naming the field the client warps itself to, so the scene plays on the map
// before its target. Scene00/Scene01 -> 900090001, Scene1 -> 900090002, Scene20/Scene21 ->
// 900090003, Scene3 -> 900090004. 900090004 has no scene of its own and ends the chain.
function start(ms) {
    switch (ms.getMapId()) {
        case 900090000:
            ms.lockUI();
            ms.showIntro("Effect/Direction4.img/promotion/Scene0" + ms.getPlayer().getGender());
            break;
        case 900090001:
            ms.showIntro("Effect/Direction4.img/promotion/Scene1");
            break;
        case 900090002:
            ms.showIntro("Effect/Direction4.img/promotion/Scene2" + ms.getPlayer().getGender());
            break;
        case 900090003:
            ms.showIntro("Effect/Direction4.img/promotion/Scene3");
            break;
        case 900090004:
            ms.unlockUI();
            break;
    }
}