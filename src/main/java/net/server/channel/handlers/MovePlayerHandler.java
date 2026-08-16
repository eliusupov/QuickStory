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

import client.Client;
import constants.net.ServerConstants;
import net.packet.InPacket;
import tools.PacketCreator;
import tools.exceptions.EmptyMovementException;

public final class MovePlayerHandler extends AbstractMovementPacketHandler {
    // Bytes before the movement-command count. v83 is 9. v84 prefixes 24 more - a second 8-byte
    // block, a 4-byte constant and an 8-byte value, then the origin x/y as two shorts.
    //
    // Measured from a live v84 capture (tools/v84, 2026-08-16 20:21). With skip(9) the next byte
    // reads 0xFF, i.e. 255 movement commands in an 80-byte packet: updatePosition then throws
    // EmptyMovementException, which this handler CATCHES AND IGNORES - so the player's position
    // silently never updates. The server keeps thinking the character is at its spawn point, and
    // ChangeMapHandler's `distanceSq > 400000` check then refuses every portal with no log line.
    //
    // With skip(33) the count byte reads 02 / 01 / 04 across three captured packets, and in each
    // case exactly that many well-formed 15-byte commands follow, then the trailer. Three for three.
    private static final int V83_MOVEMENT_HEADER = 9;
    private static final int V84_MOVEMENT_HEADER = 33;

    @Override
    public final void handlePacket(InPacket p, Client c) {
        p.skip(ServerConstants.VERSION >= 84 ? V84_MOVEMENT_HEADER : V83_MOVEMENT_HEADER);
        try {   // thanks Sa for noticing empty movement sequences crashing players
            int movementDataStart = p.getPosition();
            updatePosition(p, c.getPlayer(), 0);
            long movementDataLength = p.getPosition() - movementDataStart; //how many bytes were read by updatePosition
            p.seek(movementDataStart);

            c.getPlayer().getMap().movePlayer(c.getPlayer(), c.getPlayer().getPosition());
            if (c.getPlayer().isHidden()) {
                c.getPlayer().getMap().broadcastGMMessage(c.getPlayer(), PacketCreator.movePlayer(c.getPlayer().getId(), p, movementDataLength), false);
            } else {
                c.getPlayer().getMap().broadcastMessage(c.getPlayer(), PacketCreator.movePlayer(c.getPlayer().getId(), p, movementDataLength), false);
            }
        } catch (EmptyMovementException e) {
        }
    }
}
