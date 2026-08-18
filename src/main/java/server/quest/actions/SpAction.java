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
 * The first award whose job list contains the player's job wins; if none does, the first award is
 * paid anyway. <b>The award's own {@code job} scope names the skill book, not the player's current
 * job.</b> The two rules agree whenever an award matches - a matching award is scoped to the job
 * the player holds - so they differ only for a player who has moved on, and there the scope is the
 * one that is right: {@code sp_value 1, job/0 = 2200} is a point that belongs to Evan's 1st growth
 * whatever he has grown into since.
 * <p>
 * Paying the <em>current</em> book instead was a live defect. Every Evan growth quest stays
 * completable after the advancement that ends its growth ({@code Check.img} admits 2200-2218 on all
 * 28) but only paid while the player still held the scoped job, so advancing with quests open threw
 * the points away - and since level-up SP moves to the next book at the same instant, the old book
 * could never be refilled. The window is as narrow as the player wants it to be: char 51 lost three
 * points to an advancement taken 10ms after handing in the quest before them.
 * <p>
 * Measured over the server's {@code Act.img}: all 28 {@code sp} nodes are Evan's, each carries
 * exactly one award, each award exactly one job, and every quest's {@code lvmin} is at or above its
 * scoped job's advancement level. So a point can land in a growth the player has not reached yet
 * only if they delayed advancing, and there it waits rather than vanishing -
 * {@code AssignSPProcessor.canSPAssign} refuses skills outside the current job tree.
 * <p>
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

        /**
         * The job whose skill book this award belongs to.
         * <p>
         * The player's own job whenever the award covers it - that is the growth they earned it
         * in, and it is what disambiguates a list spanning two growths, which have separate books.
         * Only once the player has moved past the whole list does the list's first job stand in,
         * which is the case this class exists to get right. An award with no {@code job} child has
         * no growth of its own at all, so it always follows the player.
         */
        int skillBookJob(int jobId) {
            return appliesTo(jobId) ? jobId : jobs.getFirst();
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
        Award award = awardFor(jobId);   // ponytail: one award per player - the list is job branches, not a sum
        if (award != null && award.sp() != 0) {
            chr.gainSp(award.sp(), GameConstants.getSkillBook(award.skillBookJob(jobId)), false);
        }
    }

    /** The award written for this job, or - so the reward is never simply lost - the first one. */
    private Award awardFor(int jobId) {
        for (Award award : awards) {
            if (award.appliesTo(jobId)) {
                return award;
            }
        }
        return awards.isEmpty() ? null : awards.getFirst();
    }
}
