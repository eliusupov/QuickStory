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
package server.quest.actions;

import client.Character;
import config.YamlConfig;
import provider.Data;
import provider.DataTool;
import server.quest.Quest;
import server.quest.QuestActionType;

/**
 * @author Tyler (Twdtwd)
 */
public class ExpAction extends AbstractQuestAction {
    int exp;

    public ExpAction(Quest quest, Data data) {
        super(QuestActionType.EXP, quest);
        processData(data);
    }


    @Override
    public void processData(Data data) {
        exp = DataTool.getInt(data);
    }

    @Override
    public void run(Character chr, Integer extSelection) {
        runAction(chr, exp);
    }

    public static void runAction(Character chr, int gain) {
        if (chr.isBeginnerJob()) {
            // Quest exp is flat 1x until the character takes a 1st job advancement. isBeginnerJob()
            // is the gate because it already covers 2001 - Evan's beginner job, whose 1st job is
            // 2200, not 2100 - alongside 0/1000/2000.
            // Both quest exp paths land here: Act.img rewards via run(), and the qm.gainExp(n) calls
            // inside scripts/quest/*.js via QuestActionManager.gainExp, which is the only other
            // caller of this method. Nothing outside quests routes through it.
            chr.gainExp(gain, true, true);
        } else if (!YamlConfig.config.server.USE_QUEST_RATE) {
            chr.gainExp(gain * chr.getExpRate(), true, true);
        } else {
            chr.gainExp(gain * chr.getQuestExpRate(), true, true);
        }
    }
} 
