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

// Maps 914000000, 914000300, 914000400, 914000410, 914000420 and 914000500 - the Black Road
// fields an Aran walks alone during the tutorial.
// Effect.wz has NO Direction node named "aranTutorAlone" (checked Direction.img through
// Direction4.img), so this is not a cutscene. What these maps have in common is that entry to
// them follows a UI lock:
//   914000000 is where LegendCreator drops a brand new Aran (MapId.ARAN_TUTORIAL_START),
//   914000400 is entered straight out of the "ClickChild" intro fired by the aranTutorLost
//   portal on 914000300, and 914000300 itself is entered from quest 21001's forced warp.
// So the entry hook owes exactly what iceCave.js and evanAlone.js owe: release the lock.
function start(ms) {
    ms.unlockUI();
}
