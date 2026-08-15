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
package server.life;

import client.Character;
import net.server.Server;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;

public class SpawnPoint {
    // Field bosses respawn on a flat cooldown instead of their wz mobTime, matching the AreaBoss*.js
    // spawners (King Clang, Mano, Dyle, ...) which poll every 30s and respawn as soon as the boss is
    // gone. ponytail: one constant, per-boss timers if they ever need to differ.
    private static final long BOSS_RESPAWN_MILLIS = SECONDS.toMillis(30);

    // ...but only for bosses on a real timer. Plenty of PQ/event mobs carry the wz boss flag with
    // mobTimes of 1-50s by design (Monster Carnival's Buffy/Rombot at 1s, Watermelon Guard at 3-8s,
    // P Junior at 1s). Rerouting those to 30s would slow them down and break the content, so anything
    // under this stays on its own mobTime. Every genuinely starved field boss is >= 600s.
    private static final int BOSS_MIN_MOB_TIME = 60;

    private final int monster;
    private final int mobTime;
    private final int team;
    private final int fh;
    private final int f;
    private final Point pos;
    private long nextPossibleSpawn;
    private int mobInterval = 5000;
    private final AtomicInteger spawnedMonsters = new AtomicInteger(0);
    private final boolean immobile;
    private final boolean boss;
    private boolean denySpawn = false;

    public SpawnPoint(final Monster monster, Point pos, boolean immobile, int mobTime, int mobInterval, int team) {
        this.monster = monster.getId();
        this.pos = new Point(pos);
        this.mobTime = mobTime;
        this.boss = monster.isBoss();
        this.team = team;
        this.fh = monster.getFh();
        this.f = monster.getF();
        this.immobile = immobile;
        this.mobInterval = mobInterval;
        this.nextPossibleSpawn = Server.getInstance().getCurrentTime();
    }

    public int getSpawned() {
        return spawnedMonsters.intValue();
    }

    public void setDenySpawn(boolean val) {
        denySpawn = val;
    }

    public boolean getDenySpawn() {
        return denySpawn;
    }

    public boolean shouldSpawn() {
        if (denySpawn || mobTime < 0 || spawnedMonsters.get() > 0) {
            return false;
        }
        return nextPossibleSpawn <= Server.getInstance().getCurrentTime();
    }

    public boolean shouldForceSpawn() {
        return mobTime >= 0 && spawnedMonsters.get() <= 0;
    }

    public Monster getMonster() {
        Monster mob = new Monster(LifeFactory.getMonster(monster));
        mob.setPosition(new Point(pos));
        mob.setTeam(team);
        mob.setFh(fh);
        mob.setF(f);
        spawnedMonsters.incrementAndGet();
        mob.addListener(new MonsterListener() {
            @Override
            public void monsterKilled(int aniTime) {
                nextPossibleSpawn = Server.getInstance().getCurrentTime();
                if (mobTime > 0) {
                    nextPossibleSpawn += isTimedBoss() ? BOSS_RESPAWN_MILLIS : SECONDS.toMillis(mobTime);
                } else {
                    nextPossibleSpawn += aniTime;
                }
                spawnedMonsters.decrementAndGet();
            }

            @Override
            public void monsterDamaged(Character from, int trueDmg) {}

            @Override
            public void monsterHealed(int trueHeal) {}
        });
        if (mobTime == 0) {
            nextPossibleSpawn = Server.getInstance().getCurrentTime() + mobInterval;
        }
        return mob;
    }

    public int getMonsterId() {
        return monster;
    }

    public Point getPosition() {
        return pos;
    }

    public final int getF() {
        return f;
    }

    public final int getFh() {
        return fh;
    }

    public int getMobTime() {
        return mobTime;
    }

    public boolean isTimedBoss() {
        return boss && mobTime >= BOSS_MIN_MOB_TIME;
    }

    public int getTeam() {
        return team;
    }
}
