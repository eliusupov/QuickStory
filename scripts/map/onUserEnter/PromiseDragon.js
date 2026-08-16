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

// Map 900090101. Effect/Direction4.img/PromiseDragon has ONLY Scene0 - unlike its siblings
// meetWithDragon / getDragonEgg / crash, which each carry Scene0 AND Scene1. PromiseDragon's
// Scene0 is a gender-neutral "word" text effect (visual .../effect/PromiseDragon/word) plus a
// type=2 warp to field 100030100; there is no character sprite in it, so v84 never needed a
// second gendered copy. Appending getGender() therefore built ".../Scene1" for a FEMALE Evan -
// a path that does not exist - and showIntro on a missing path crashes the client. Since map
// 900090101 has no portal (only `sp`), that Scene0 warp is the map's ONLY exit, so the crash
// also stranded the character. Always use Scene0. Same defect class as incubation_dragon.js.
function start(ms) {
    ms.lockUI();
    ms.showIntro("Effect/Direction4.img/PromiseDragon/Scene0");
}