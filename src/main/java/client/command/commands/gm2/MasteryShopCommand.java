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

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm2;

import client.Client;
import client.command.Command;
import server.ShopFactory;

public class MasteryShopCommand extends Command {
    {
        setDescription("Open Sly's mastery book shop.");
    }

    @Override
    public void execute(Client c, String[] params) {
        // Sly (Leafre : Department Store). The only shop on the server selling mastery
        // books -- see 166-evan-shops-data.sql. NPC 2080001, shops row (2080001, 2080001).
        ShopFactory.getInstance().getShop(2080001).sendShop(c);
    }
}
