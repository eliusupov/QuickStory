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

import tools.DatabaseConnection;
import tools.PacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class MonsterBook {
    private int specialCard = 0;
    private int normalCard = 0;
    private int bookLevel = 1;
    private final Map<Integer, Integer> cards = new LinkedHashMap<>();
    private final Lock lock = new ReentrantLock();
    private final Map<Integer, Boolean> isGainedMainStatBuffs = new LinkedHashMap<>();

    public Set<Entry<Integer, Integer>> getCardSet() {
        lock.lock();
        try {
            return new HashSet<>(cards.entrySet());
        } finally {
            lock.unlock();
        }
    }

    public void addCard(final Client c, final int cardid) {
        c.getPlayer().getMap().broadcastMessage(c.getPlayer(), PacketCreator.showForeignCardEffect(c.getPlayer().getId()), false);

        Integer qty;
        boolean blockedSetCompletion = false;
        lock.lock();
        try {
            qty = cards.get(cardid);

            if (qty != null) {
                if (qty < 5) {
                    // Block beginners under level 10 from completing a set (4 -> 5). isBeginnerJob(),
                    // not isA(BEGINNER): the latter is only ever true for job 0, so it missed the
                    // Noblesse (1000), Legend (2000) and Evan (2001) beginner jobs.
                    if (qty == 4 && c.getPlayer().getLevel() < 10 && c.getPlayer().isBeginnerJob()) {
                        blockedSetCompletion = true;
                    } else {
                        cards.put(cardid, qty + 1);
                    }
                    if (!blockedSetCompletion && qty + 1 == 5) { // Card count reached 5
                        if (!isGainedMainStatBuffs.getOrDefault(cardid, false)) {
                            // Prevent beginners under level 10 from receiving the buff
                            if (!(c.getPlayer().getLevel() < 10 && c.getPlayer().isBeginnerJob())) {
                                applyMainStatBuff(c, cardid);
                                isGainedMainStatBuffs.put(cardid, true);
                            }
                        }
                    }
                }
            } else {
                cards.put(cardid, 1);
                qty = 0;

                if (cardid / 1000 >= 2388) {
                    specialCard++;
                } else {
                    normalCard++;
                }
            }
        } finally {
            lock.unlock();
        }

        if (blockedSetCompletion) {
            c.getPlayer().dropMessage(5, "[Monster card] You cannot complete a set until you get a job.");
            return;
        }

        if (qty < 5) {
            if (qty == 0) {     // leveling system only accounts unique cards
                calculateLevel();
            }

            c.sendPacket(PacketCreator.addCard(false, cardid, qty + 1));
            c.sendPacket(PacketCreator.showGainCard());
        } else {
            c.sendPacket(PacketCreator.addCard(true, cardid, 5));
            if (!isGainedMainStatBuffs.getOrDefault(cardid, false)) {
                // Prevent beginners under level 10 from receiving the buff
                if (!(c.getPlayer().getLevel() < 10 && c.getPlayer().isBeginnerJob())) {
                    applyMainStatBuff(c, cardid);
                    isGainedMainStatBuffs.put(cardid, true);
                }
            }
        }
    }

    private void applyMainStatBuff(Client c, int cardid) {
        Character player = c.getPlayer();
        Job job = player.getJob();
        Stat mainStat = null;

        if (job.isA(Job.WARRIOR) || job.isA(Job.ARAN1) || job.isA(Job.DAWNWARRIOR1)) {
            mainStat = Stat.STR;
        } else if (job.isA(Job.MAGICIAN) || job.isA(Job.BLAZEWIZARD1) || job.isA(Job.EVAN1) || job == Job.EVAN) {
            // Evan is a magician class, but isA(MAGICIAN) is false for it (22 vs 2), so without
            // naming it here every Evan fell through to the STR default below. Job.EVAN (2001) is
            // Evan's beginner job and only ever advances into 2200, so it is INT too.
            mainStat = Stat.INT;
        } else if (job.isA(Job.BOWMAN) || job.isA(Job.WINDARCHER1)) {
            mainStat = Stat.DEX;
        } else if (job.isA(Job.THIEF) || job.isA(Job.NIGHTWALKER1)) {
            mainStat = Stat.LUK;
        } else if (job.isA(Job.PIRATE) || job.isA(Job.THUNDERBREAKER1)) {
            int jobid = job.getId();
            // Thunder Breakers are STR based (knuckle)
            if (jobid >= Job.THUNDERBREAKER1.getId() && jobid <= Job.THUNDERBREAKER4.getId()) {
                mainStat = Stat.STR;
            } else if (jobid == Job.BRAWLER.getId() || jobid == Job.MARAUDER.getId() || jobid == Job.BUCCANEER.getId()) {
                mainStat = Stat.STR; // Knuckle Pirates
            } else if (jobid == Job.GUNSLINGER.getId() || jobid == Job.OUTLAW.getId() || jobid == Job.CORSAIR.getId()) {
                mainStat = Stat.DEX; // Gun Pirates
            } else {
                mainStat = Stat.DEX; // Default for other pirates
            }
        } else {
            // Default to STR for any unhandled jobs, including beginner
            mainStat = Stat.STR;
        }

        if (mainStat != null) {
            // Update the internal stat field directly
            if (mainStat == Stat.STR) {
                player.str += 1;
                player.updateSingleStat(mainStat, player.str); // Send packet to client
            } else if (mainStat == Stat.DEX) {
                player.dex += 1;
                player.updateSingleStat(mainStat, player.dex); // Send packet to client
            } else if (mainStat == Stat.INT) {
                player.int_ += 1;
                player.updateSingleStat(mainStat, player.int_); // Send packet to client
            } else if (mainStat == Stat.LUK) {
                player.luk += 1;
                player.updateSingleStat(mainStat, player.luk); // Send packet to client
            }
            player.saveCharToDB(); // Persist the stat change to the database
            player.dropMessage(5, "You have completed a Monster Card set and gained +1 " + mainStat.toString() + "!");
        } else {
            player.dropMessage(5, "You have completed a Monster Card set, but your main stat could not be determined.");
        }
    }

    private void calculateLevel() {
        lock.lock();
        try {
            int collectionExp = (normalCard + specialCard);

            int level = 0, expToNextlevel = 1;
            do {
                level++;
                expToNextlevel += level * 10;
            } while (collectionExp >= expToNextlevel);

            bookLevel = level;  // thanks IxianMace for noticing book level differing between book UI and character info UI
        } finally {
            lock.unlock();
        }
    }

    public int getBookLevel() {
        lock.lock();
        try {
            return bookLevel;
        } finally {
            lock.unlock();
        }
    }

    public Map<Integer, Integer> getCards() {
        lock.lock();
        try {
            return Collections.unmodifiableMap(cards);
        } finally {
            lock.unlock();
        }
    }

    public int getTotalCards() {
        lock.lock();
        try {
            return specialCard + normalCard;
        } finally {
            lock.unlock();
        }
    }

    public int getNormalCard() {
        lock.lock();
        try {
            return normalCard;
        } finally {
            lock.unlock();
        }
    }

    public int getSpecialCard() {
        lock.lock();
        try {
            return specialCard;
        } finally {
            lock.unlock();
        }
    }

    public void loadCards(final int charid) throws SQLException {
        lock.lock();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT cardid, level, isGainedMainStatBuff FROM monsterbook WHERE charid = ? ORDER BY cardid ASC")) {
            ps.setInt(1, charid);

            try (ResultSet rs = ps.executeQuery()) {
                int cardid;
                int level;
                while (rs.next()) {
                    cardid = rs.getInt("cardid");
                    level = rs.getInt("level");
                    boolean isGainedMainStatBuff = rs.getInt("isGainedMainStatBuff") == 1;
                    if (cardid / 1000 >= 2388) {
                        specialCard++;
                    } else {
                        normalCard++;
                    }
                    cards.put(cardid, level);
                    isGainedMainStatBuffs.put(cardid, isGainedMainStatBuff);
                }
            }
        } finally {
            lock.unlock();
        }

        calculateLevel();
    }

    public void saveCards(Connection con, int chrId) throws SQLException {
        final String query = """
                INSERT INTO monsterbook (charid, cardid, level, isGainedMainStatBuff)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE level = ?, isGainedMainStatBuff = ?;
                """;
        try (final PreparedStatement ps = con.prepareStatement(query)) {
            for (Map.Entry<Integer, Integer> cardAndLevel : cards.entrySet()) {
                final int card = cardAndLevel.getKey();
                final int level = cardAndLevel.getValue();
                boolean isGainedMainStatBuff = isGainedMainStatBuffs.getOrDefault(card, false);
                // insert
                ps.setInt(1, chrId);
                ps.setInt(2, card);
                ps.setInt(3, level);
                ps.setInt(4, isGainedMainStatBuff ? 1 : 0);

                // update
                ps.setInt(5, level);
                ps.setInt(6, isGainedMainStatBuff ? 1 : 0);

                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static int[] getCardTierSize() {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM monstercarddata GROUP BY floor(cardid / 1000);", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
             ResultSet rs = ps.executeQuery()) {
            rs.last();
            int[] tierSizes = new int[rs.getRow()];
            rs.beforeFirst();

            while (rs.next()) {
                tierSizes[rs.getRow() - 1] = rs.getInt(1);
            }

            return tierSizes;
        } catch (SQLException e) {
            e.printStackTrace();
            return new int[0];
        }
    }
}
