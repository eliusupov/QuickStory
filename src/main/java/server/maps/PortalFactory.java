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
package server.maps;

import provider.Data;
import provider.DataTool;

import java.awt.*;

public class PortalFactory {
    private int nextDoorPortal;

    public PortalFactory() {
        nextDoorPortal = 0x80;
    }

    public Portal makePortal(int type, Data portal) {
        GenericPortal ret = null;
        if (type == Portal.MAP_PORTAL) {
            ret = new MapPortal();
        } else {
            ret = new GenericPortal(type);
        }
        loadPortal(ret, portal);
        return ret;
    }

    /**
     * The whole of what the server reads out of a {@code portal} node: {@code pn}, {@code tn},
     * {@code tm}, {@code x}, {@code y}, {@code script}, and the node's <em>name</em>. Nothing else.
     * {@code onlyOnce}, {@code hideTooltip}, {@code delay}, {@code horizontalImpact} and
     * {@code image} appear in the WZ and are read <strong>nowhere</strong> in this codebase - the
     * client uses its own copy of them, exactly as it does for foothold geometry.
     * <p>
     * That is what makes a portal section safe to take from a newer client wholesale: only
     * {@code script} is server-owned (it names a file under {@code scripts/portal/}, and a name we
     * cannot resolve is worse than none), while every other leaf is the client's business. See
     * ticket 53.
     * <p>
     * The node name is an <em>array index</em>, and {@code PacketCreator.getWarpToMap} sends
     * {@code portal.getId()} raw for the client to resolve against its own array - so inserting or
     * reordering a slot changes where arriving players land, even though clicking a portal is
     * resolved by name. {@code DOOR_PORTAL} is the exception: it is handed a synthetic
     * {@code 0x80+n} id below and never addresses by position.
     */
    private void loadPortal(GenericPortal myPortal, Data portal) {
        myPortal.setName(DataTool.getString(portal.getChildByPath("pn")));
        myPortal.setTarget(DataTool.getString(portal.getChildByPath("tn")));
        myPortal.setTargetMapId(DataTool.getInt(portal.getChildByPath("tm")));
        int x = DataTool.getInt(portal.getChildByPath("x"));
        int y = DataTool.getInt(portal.getChildByPath("y"));
        myPortal.setPosition(new Point(x, y));
        String script = DataTool.getString("script", portal, null);
        if (script != null && script.equals("")) {
            script = null;
        }
        myPortal.setScriptName(script);
        if (myPortal.getType() == Portal.DOOR_PORTAL) {
            myPortal.setId(nextDoorPortal);
            nextDoorPortal++;
        } else {
            myPortal.setId(Integer.parseInt(portal.getName()));
        }
    }
}
