/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package server.events;

import client.Character;
import client.SkillFactory;

import static java.util.concurrent.TimeUnit.DAYS;

/**
 * @author kevintjuh93
 */
public class RescueGaga extends Events {

    private int completed;

    public RescueGaga(int completed) {
        super();
        this.completed = completed;
    }

    public int getCompleted() {
        return completed;
    }

    public void complete() {
        completed++;
    }

    @Override
    public int getInfo() {
        return getCompleted();
    }

    public void giveSkill(Character chr) {
        int skillid = 0;
        switch (chr.getJobType()) {
            case 0:
                skillid = 1013;
                break;
            case 1:
            case 2:
                // jobType 2 is Aran AND Evan, and both are handed the Cygnus ids on purpose: neither
                // 20001014-16 nor 20011014-16 exist in Skill.wz (2000.img stops at 20001013, 2001.img
                // has no 1013-1016 at all), so there is no Legend-side reward to point them at.
                // Giving Evan its "own" id here would hand changeSkillLevel a null skill.
                skillid = 10001014;
        }

        long expiration = (System.currentTimeMillis() + DAYS.toMillis(20));
        if (completed < 20) {
            chr.changeSkillLevel(SkillFactory.getSkill(skillid), (byte) 1, 1, expiration);
            chr.changeSkillLevel(SkillFactory.getSkill(skillid + 1), (byte) 1, 1, expiration);
            chr.changeSkillLevel(SkillFactory.getSkill(skillid + 2), (byte) 1, 1, expiration);
        } else {
            chr.changeSkillLevel(SkillFactory.getSkill(skillid), (byte) 2, 2, chr.getSkillExpiration(skillid));
        }
    }

}
