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
import server.movement.LifeMovementFragment;
import tools.PacketCreator;
import tools.exceptions.EmptyMovementException;

import java.util.List;

public final class MovePetHandler extends AbstractMovementPacketHandler {
    // NO v84 gate here, and that is measured. The v84 "dr words" pass rewrote (and virtualised)
    // CVecCtrlUser::EndUpdateActive alone - MOVE_PLAYER 9 -> 33, 404ec864d. The pet's encoder and the
    // shared movement blob were both left alone:
    //
    //   CVecCtrlPet::EndUpdateActive   v83 0x009C4E41   v84 0x00A0C600
    //     opcode push      v83 0x009C4E65 (0xA7)   v84 0x00A0C624 (0xAC)
    //     COutPacket ctor  v83 0x009C4E6D          v84 0x00A0C62C
    //     EncodeBuffer(pet+0xA0, 8) = m_liPetLockerSN   v83 0x009C4E8F   v84 0x00A0C64E
    //       (EncodeBuffer itself: v83 0x0046C00C, v84 0x0046E5FE - memcpy of exactly nLen bytes)
    //     CMovePath::Flush v83 0x009C4E9E          v84 0x00A0C65D
    //   CMovePath::Encode  v83 0x0068A563          v84 0x006A121A  - identical instruction for
    //     instruction; Encode2 startX / Encode2 startY / Encode1 count at v83 0x0068A57C, 0x0068A592,
    //     0x0068A5C3 and v84 0x006A1233, 0x006A1249, 0x006A127A.
    //
    // So the 12 bytes below are 8 (locker SN) + 4 (CMovePath origin) in BOTH versions - which is why
    // parseMovement, unlike its siblings, reads the command count straight away: the readLong has
    // already eaten the origin. Getting it wrong is silent - parseMovement throws
    // EmptyMovementException, the catch below returns, the pet never moves, nothing is logged.
    // MovementHeaderTest pins it.
    @Override
    public final void handlePacket(InPacket p, Client c) {
        int petId = p.readInt();
        p.readLong();
//        Point startPos = StreamUtil.readShortPoint(slea);
        List<LifeMovementFragment> res;

        try {
            res = parseMovement(p);
        } catch (EmptyMovementException e) {
            return;
        }
        Character player = c.getPlayer();
        byte slot = player.getPetIndex(petId);
        if (slot == -1) {
            return;
        }
        player.getPet(slot).updatePosition(res);
        player.getMap().broadcastMessage(player, PacketCreator.movePet(player.getId(), petId, slot, res), false);
    }
}
