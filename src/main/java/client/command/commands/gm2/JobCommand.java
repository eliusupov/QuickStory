/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
    Copyleft (L) 2016 - 2019 RonanLana

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

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm2;

import client.Character;
import client.Client;
import client.Job;
import client.command.Command;

public class JobCommand extends Command {
    {
        setDescription("Change job of a player.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length == 1) {
            changeJob(player, player, params[0]);
        } else if (params.length == 2) {
            Character victim = c.getWorldServer().getPlayerStorage().getCharacterByName(params[0]);

            if (victim != null) {
                changeJob(player, victim, params[1]);
            } else {
                player.message("Player '" + params[0] + "' could not be found.");
            }
        } else {
            player.message("Syntax: !job <job id> <opt: IGN of another person>");
        }
    }

    /**
     * Accepts exactly the job ids {@link Job} defines and rejects the rest with a message.
     * <p>
     * Do not replace this with a numeric range. The guard was {@code jobid < 0 || jobid >= 2200},
     * which rejected every one of Evan's ten job levels (EVAN1 2200 .. EVAN10 2218) and so made an
     * Evan unreachable by the only GM route there is, while at the same time letting any unknown id
     * below 2200 through to {@code changeJob(null)} — an early return with no feedback at all.
     */
    private static void changeJob(Character actor, Character target, String rawJobId) {
        Job job = Job.getById(Integer.parseInt(rawJobId));
        if (job == null) {
            actor.message("Jobid " + rawJobId + " is not available.");
            return;
        }

        target.changeJob(job);
        actor.equipChanged();
    }
}
