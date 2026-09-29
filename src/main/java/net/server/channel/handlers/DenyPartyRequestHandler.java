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
import net.server.coordinator.world.InviteCoordinator;
import net.server.coordinator.world.InviteCoordinator.InviteResult;
import net.server.coordinator.world.InviteCoordinator.InviteResultType;
import net.server.coordinator.world.InviteCoordinator.InviteType;
import net.server.world.Party;
import tools.PacketCreator;

public final class DenyPartyRequestHandler extends AbstractPacketHandler {

    @Override
    public final void handlePacket(InPacket p, Client c) {
        int action = p.readByte();
        if (action != 0x19 && action != 0x1A) {
            return;
        }

        int partyId = p.readInt();
        Character chr = c.getPlayer();
        boolean accepted = action == 0x1A;
        InviteResult invite = InviteCoordinator.answerInvite(InviteType.PARTY, chr.getId(), partyId, accepted);

        if (accepted) {
            if (invite.result == InviteResultType.ACCEPTED) {
                Party.joinParty(chr, partyId, false);
            } else {
                c.sendPacket(PacketCreator.serverNotice(5, "You couldn't join the party due to an expired invitation request."));
            }
        } else if (invite.result == InviteResultType.DENIED) {
            chr.updatePartySearchAvailability(chr.getParty() == null);
            if (invite.from != null) {
                invite.from.sendPacket(PacketCreator.partyStatusMessage(23, chr.getName()));
            }
        }
    }
}
