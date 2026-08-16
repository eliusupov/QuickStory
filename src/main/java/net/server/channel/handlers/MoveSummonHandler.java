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
import net.packet.InPacket;
import server.maps.Summon;
import tools.PacketCreator;
import tools.exceptions.EmptyMovementException;

import java.awt.*;
import java.util.Collection;

public final class MoveSummonHandler extends AbstractMovementPacketHandler {
    // NO v84 gate here, and that is measured. The v84 "dr words" pass rewrote (and virtualised)
    // CVecCtrlUser::EndUpdateActive alone - MOVE_PLAYER 9 -> 33, 404ec864d. The summon's encoder and
    // the shared movement blob were both left alone:
    //
    //   CVecCtrlSummoned::EndUpdateActive   v83 0x009C84E9   v84 0x00A0FD89
    //     opcode push      v83 0x009C8523 (0xAF)   v84 0x00A0FDC3 (0xB4)
    //     COutPacket ctor  v83 0x009C852B          v84 0x00A0FDCB
    //     Encode4 owner cid ([this+0x248])  v83 0x009C853D   v84 0x00A0FDDD   <- the readInt below
    //     CMovePath::Flush v83 0x009C854C          v84 0x00A0FDEC
    //   CMovePath::Encode  v83 0x0068A563          v84 0x006A121A  - identical instruction for
    //     instruction; Encode2 startX / Encode2 startY / Encode1 count at v83 0x0068A57C, 0x0068A592,
    //     0x0068A5C3 and v84 0x006A1233, 0x006A1249, 0x006A127A. 4 + 4 == 8 in both versions.
    //
    // The opcode push at 0x00A0FDC3 is 0xB4, and recvops-84.properties used to say 0xB2 - so until
    // 6ea21ac2d this handler was never reached at v84 at all and the header below was moot. That is
    // fixed; the header is now what decides whether summon movement works.
    //
    // Getting the header wrong is silent: updatePosition throws EmptyMovementException, the catch
    // below swallows it, the summon never moves and nothing is logged. MovementHeaderTest pins it.
    @Override
    public final void handlePacket(InPacket p, Client c) {
        int oid = p.readInt();
        Point startPos = new Point(p.readShort(), p.readShort());
        Character player = c.getPlayer();
        Collection<Summon> summons = player.getSummonsValues();
        Summon summon = null;
        for (Summon sum : summons) {
            if (sum.getObjectId() == oid) {
                summon = sum;
                break;
            }
        }
        if (summon != null) {
            try {
                int movementDataStart = p.getPosition();
                updatePosition(p, summon, 0);
                long movementDataLength = p.getPosition() - movementDataStart; //how many bytes were read by updatePosition
                p.seek(movementDataStart);

                player.getMap().broadcastMessage(player, PacketCreator.moveSummon(player.getId(), oid, startPos, p, movementDataLength), summon.getPosition());
            } catch (EmptyMovementException e) {
            }
        }
    }
}
