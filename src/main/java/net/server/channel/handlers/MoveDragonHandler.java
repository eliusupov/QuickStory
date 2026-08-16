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
import server.maps.Dragon;
import tools.PacketCreator;
import tools.exceptions.EmptyMovementException;

import java.awt.*;


public class MoveDragonHandler extends AbstractMovementPacketHandler {
    // NO v84 gate here, and that is measured, not assumed. MOVE_PLAYER needed one (9 -> 33 bytes,
    // 404ec864d) because the v84 anti-cheat "dr words" pass rewrote CVecCtrlUser::EndUpdateActive
    // alone - and virtualised it, which is why only that one encoder is unreadable in the image.
    // It did NOT touch the shared movement blob or the dragon's own encoder:
    //
    //   CMovePath::Encode   v83 0x0068A563   v84 0x006A121A   instruction-for-instruction identical
    //     Encode2 startX    v83 0x0068A57C   v84 0x006A1233
    //     Encode2 startY    v83 0x0068A592   v84 0x006A1249
    //     Encode1 count     v83 0x0068A5C3   v84 0x006A127A
    //
    //   CVecCtrlDragon::EndUpdateActive   v83 0x009B7B9C   v84 0x009FF057
    //     opcode push       v83 0x009B7BBD (0xB5)   v84 0x009FF078 (0xBA)
    //     COutPacket ctor   v83 0x009B7BC5          v84 0x009FF080
    //     CMovePath::Flush  v83 0x009B7BD8          v84 0x009FF093
    //   Zero Encode* calls between the ctor and the Flush in EITHER version: the dragon writes no
    //   packet-level prologue at all, so the header is just CMovePath's 4-byte origin. 4 == 4.
    //
    // Getting this wrong is silent: updatePosition throws EmptyMovementException, the catch below
    // swallows it, the dragon never moves and nothing is logged. MovementHeaderTest pins it.
    @Override
    public void handlePacket(InPacket p, Client c) {
        final Character chr = c.getPlayer();
        final Point startPos = new Point(p.readShort(), p.readShort());
        final Dragon dragon = chr.getDragon();
        if (dragon != null) {
            try {
                int movementDataStart = p.getPosition();
                updatePosition(p, dragon, 0);
                long movementDataLength = p.getPosition() - movementDataStart; //how many bytes were read by updatePosition
                p.seek(movementDataStart);

                if (chr.isHidden()) {
                    chr.getMap().broadcastGMPacket(chr, PacketCreator.moveDragon(dragon, startPos, p, movementDataLength));
                } else {
                    chr.getMap().broadcastMessage(chr, PacketCreator.moveDragon(dragon, startPos, p, movementDataLength), dragon.getPosition());
                }
            } catch (EmptyMovementException e) {
            }
        }
    }
}