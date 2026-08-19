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
package client;

import server.StatEffect;
import server.life.Element;

import java.util.ArrayList;
import java.util.List;

public class Skill {
    private final int id;
    private final List<StatEffect> effects = new ArrayList<>();
    private Element element;
    private int animationTime;
    private int mobCode;
    private final int job;
    private boolean action;

    public Skill(int id) {
        this.id = id;
        this.job = id / 10000;
    }

    public int getId() {
        return id;
    }

    public StatEffect getEffect(int level) {
        return effects.get(level - 1);
    }

    public int getMaxLevel() {
        return effects.size();
    }

    // True when the skill carries a Skill.wz masterLevel node, so the char-data skill record must
    // include the extra masterLevel int (PacketCreator.addSkillInfo) and SP is capped at masterLevel
    // rather than maxLevel (AssignSPProcessor). This mirrors the v84/v95 client's
    // SkillConstants.IsSkillNeedMasterLevel (Edelstein reference): the server MUST agree with the
    // client on whether that int is present, or the char-entry packet desyncs and the client crashes.
    public boolean isFourthJob() {
        if (job / 100 == 22) { // Evan (job race 22 magician). EvanJr (job 2001) is race 20, excluded.
            // Magic Guard/Critical/Booster carry a masterLevel node below their top growth; every
            // skill of the last two growths (2217 = jobLevel 9, 2218 = jobLevel 10) carries one too.
            if (id == 22111001 || id == 22140000 || id == 22141002) {
                return true;
            }
            return job == 2217 || job == 2218;
        }
        return job % 10 == 2;
    }

    public void setElement(Element elem) {
        element = elem;
    }

    public Element getElement() {
        return element;
    }

    public int getAnimationTime() {
        return animationTime;
    }

    public void setAnimationTime(int time) {
        animationTime = time;
    }

    public void incAnimationTime(int time) {
        animationTime += time;
    }

    public int getMobCode() {
        return mobCode;
    }

    public void setMobCode(int mobCode) {
        this.mobCode = mobCode;
    }

    public boolean isBeginnerSkill() {
        return id % 10000000 < 10000;
    }

    public void setAction(boolean act) {
        action = act;
    }

    public boolean getAction() {
        return action;
    }

    public void addLevelEffect(StatEffect effect) {
        effects.add(effect);
    }
}
