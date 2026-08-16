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
import constants.game.GameConstants;
import provider.Data;
import provider.DataTool;
import server.quest.Quest;
import server.quest.QuestActionType;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code sp} quest reward. Unlike {@code exp} / {@code money} this is not a flat int: the node
 * is a list of awards, each with its own value and its own job filter, because one quest id is
 * shared across job branches and pays a different amount to each.
 *
 * <pre>
 * &lt;imgdir name="sp"&gt;
 *   &lt;imgdir name="0"&gt;
 *     &lt;int name="sp_value" value="1"/&gt;
 *     &lt;imgdir name="job"&gt;
 *       &lt;int name="0" value="2200"/&gt;
 *     &lt;/imgdir&gt;
 *   &lt;/imgdir&gt;
 * &lt;/imgdir&gt;
 * </pre>
 *
 * The first award whose job list contains the player's job wins; if none does, nothing is paid.
 * The SP goes through {@link Character#gainSp(int, int, boolean)} keyed by
 * {@link GameConstants#getSkillBook(int)}, which is the same extended ten-slot {@code sp} column
 * Evan's per-level skill books already use — not the single-value setter.
 */
public class SpAction extends AbstractQuestAction {

    /** @param jobs empty means the award carries no {@code job} filter, i.e. it applies to anyone. */
    private record Award(int sp, List<Integer> jobs) {
        boolean appliesTo(int jobId) {
            return jobs.isEmpty() || jobs.contains(jobId);
        }
    }

    private final List<Award> awards = new ArrayList<>();

    public SpAction(Quest quest, Data data) {
        super(QuestActionType.SP, quest);
        processData(data);
    }

    @Override
    public void processData(Data data) {
        awards.clear();
        for (Data entry : data.getChildren()) {
            List<Integer> jobs = new ArrayList<>();
            Data jobData = entry.getChildByPath("job");
            if (jobData != null) {
                for (Data job : jobData.getChildren()) {
                    jobs.add(DataTool.getInt(job, -1));
                }
            }
            awards.add(new Award(DataTool.getInt(entry.getChildByPath("sp_value"), 0), jobs));
        }
    }

    @Override
    public void run(Character chr, Integer extSelection) {
        int jobId = chr.getJob().getId();
        for (Award award : awards) {
            if (award.appliesTo(jobId)) {
                if (award.sp() != 0) {
                    chr.gainSp(award.sp(), GameConstants.getSkillBook(jobId), false);
                }
                return; // ponytail: one award per player - the list is job branches, not a sum
            }
        }
    }
}
