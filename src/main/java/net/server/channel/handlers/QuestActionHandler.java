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
package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.quest.QuestScriptManager;
import server.life.NPC;
import server.life.PlayerNPC;
import server.quest.Quest;

import java.awt.*;

/**
 * @author Matze
 */
public final class QuestActionHandler extends AbstractPacketHandler {
    private static final Logger log = LoggerFactory.getLogger(QuestActionHandler.class);

    /**
     * Mir, the Evan player's dragon. He is <em>not</em> a field NPC and never was one in GMS v84:
     * {@code Etc.wz/NpcLocation.img} gives 1013000 the location {@code -1} (no field), where his
     * neighbour 1013001 gets a real {@code 900010200}. This server models him the same way Nexon
     * did - as a per-player summon, {@link server.maps.Dragon} with {@code MapObjectType.DRAGON},
     * created for every Evan past job 2001 in {@code PlayerLoggedinHandler} and on job change.
     * Quest 22500's own objective text says it outright: "Talk to him by clicking on the Baby
     * Dragon". So he is never in any map's {@code life}, and {@code getNPCById} can never find him.
     */
    private static final int MIR = 1013000;

    /**
     * Where the quest giver stands, or {@code null} if it is not on this map. A Hall-of-Fame
     * PlayerNPC is a {@code MapObjectType.PLAYER_NPC} and never a {@link NPC}, so
     * {@code getNPCById} cannot see one - quest 22402 "Meeting the Dragon Rider" names 9901000,
     * the first warrior slot of the {@code NpcId.PLAYER_NPC_BASE} band, and both its start and its
     * end would be refused on a map where that pnpc is standing in plain sight.
     */
    private static Point questNpcPosition(Character player, int npcId) {
        NPC npc = player.getMap().getNPCById(npcId);
        if (npc != null) {
            return npc.getPosition();
        }

        PlayerNPC pnpc = player.getMap().getPlayerNPCByScriptId(npcId);
        return pnpc != null ? pnpc.getPosition() : null;
    }

    // isNpcNearby thanks to GabrielSin
    private static boolean isNpcNearby(InPacket p, Character player, Quest quest, int npcId) {
        Point playerP;
        Point pos = player.getPosition();

        if (p.available() >= 4) {
            playerP = new Point(p.readShort(), p.readShort());
            if (playerP.distance(pos) > 1000) {     // thanks Darter (YungMoozi) for reporting unchecked player position
                playerP = pos;
            }
        } else {
            playerP = pos;
        }

        if (!quest.isAutoStart() && !quest.isAutoComplete()) {
            // A summoned Mir is always at his owner's position, so "is the npc nearby" is answered
            // by "does this player have their dragon out" - the map lookup below never can. Without
            // this, 25 Evan quest starts and 22 ends are refused outright, beginning with 22500
            // "Baby Dragon Awakens", the quest immediately after the 1st job advancement. The ten
            // job advancements survive only because they are autoStart and skip this whole block.
            if (npcId == MIR && player.getDragon() != null) {
                return true;
            }

            Point npcP = questNpcPosition(player, npcId);
            if (npcP == null) {
                log.debug("Quest {} denied for {}: npc {} is not spawned on map {}", quest.getId(), player.getName(),
                        npcId, player.getMapId());
                // ponytail: same nudge as the too-far case - the client lets you hit Accept from the
                // quest list on any map, and a silent return reads as "the server doesn't have it".
                player.dropMessage(5, "Approach the NPC to fulfill this quest operation.");
                return false;
            }

            if (Math.abs(npcP.getX() - playerP.getX()) > 1200 || Math.abs(npcP.getY() - playerP.getY()) > 800) {
                log.debug("Quest {} denied for {}: npc {} at {} too far from {}", quest.getId(), player.getName(),
                        npcId, npcP, playerP);
                player.dropMessage(5, "Approach the NPC to fulfill this quest operation.");
                return false;
            }
        }

        return true;
    }

    @Override
    public final void handlePacket(InPacket p, Client c) {
        byte action = p.readByte();
        short questid = p.readShort();
        Character player = c.getPlayer();
        Quest quest = Quest.getInstance(questid);
        // QUEST_ACTION sits in LoggingUtil's ignored-recv set, so without this the whole path can
        // fail through any of the silent returns below and leave no trace at all. Ticket 26.
        log.debug("QUEST_ACTION action {} quest {} from {}", action, questid, player.getName());

        switch (action) {
        case 0: // Restore lost item, Credits Darter ( Rajan )
            p.readInt();
            int itemid = p.readInt();
            quest.restoreLostItem(player, itemid);
            break;
        case 1: { // Start Quest
            int npc = p.readInt();
            if (!isNpcNearby(p, player, quest, npc)) {
                return;
            }
            if (quest.canStart(player, npc)) {
                quest.start(player, npc);
            }
            break;
        }
        case 2: { // Complete Quest
            int npc = p.readInt();
            if (!isNpcNearby(p, player, quest, npc)) {
                return;
            }
            if (quest.canComplete(player, npc)) {
                if (p.available() >= 2) {
                    int selection = p.readShort();
                    quest.complete(player, npc, selection);
                } else {
                    quest.complete(player, npc);
                }
            }
            break;
        }
        case 3: // forfeit quest
            quest.forfeit(player);
            break;
        case 4: { // scripted start quest
            int npc = p.readInt();
            if (!isNpcNearby(p, player, quest, npc)) {
                return;
            }
            if (quest.canStart(player, npc)) {
                QuestScriptManager.getInstance().start(c, questid, npc);
            }
            break;
        }
        case 5: { // scripted end quests
            int npc = p.readInt();
            if (!isNpcNearby(p, player, quest, npc)) {
                return;
            }
            if (quest.canComplete(player, npc)) {
                QuestScriptManager.getInstance().end(c, questid, npc);
            }
            break;
        }
        }
    }
}
