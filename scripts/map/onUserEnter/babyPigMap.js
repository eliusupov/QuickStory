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

function start(ms) {
    ms.unlockUI();
    // ms is a MapScriptMethods, which extends AbstractPlayerInteraction and so already carries
    // forceStartQuest. getClient().getQM() is the map of OPEN QUEST-SCRIPT SESSIONS - arriving on a
    // map is not a quest script, so it is null here and the call threw inside Graal.
    // MapScriptManager swallows that, leaving only the unlockUI above: 22015 never started, the
    // Baby Pig answered "you are too far from the Piglet" forever, and the Evan chain hard-stopped
    // at 22005. The bug was invisible until 7d291d814 made this map reachable at all.
    ms.forceStartQuest(22015);
}