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
package scripting.quest;

import client.Client;
import client.QuestStatus;
import constants.game.GameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.AbstractScriptManager;
import server.quest.Quest;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.Map;

/**
 * @author RMZero213
 */
public class QuestScriptManager extends AbstractScriptManager {
    private static final Logger log = LoggerFactory.getLogger(QuestScriptManager.class);
    private static final QuestScriptManager instance = new QuestScriptManager();

    private final Map<Client, QuestActionManager> qms = new HashMap<>();
    private final Map<Client, Invocable> scripts = new HashMap<>();

    public static QuestScriptManager getInstance() {
        return instance;
    }

    private ScriptEngine getQuestScriptEngine(Client c, short questid) {
        ScriptEngine engine = getInvocableScriptEngine("quest/" + questid + ".js", c);
        if (engine == null && GameConstants.isMedalQuest(questid)) {
            engine = getInvocableScriptEngine("quest/medalQuest.js", c);   // start generic medal quest
        }

        return engine;
    }

    public void start(Client c, short questid, int npc) {
        Quest quest = Quest.getInstance(questid);
        try {
            QuestActionManager qm = new QuestActionManager(c, questid, npc, true);
            if (qms.containsKey(c)) {
                log.debug("START quest {} ignored: {} already has quest script {} open", questid,
                        c.getPlayer().getName(), qms.get(c).getQuest());
                return;
            }
            if (!c.canClickNPC()) {
                log.debug("START quest {} ignored: {} is inside the 500ms npc-click cooldown", questid,
                        c.getPlayer().getName());
                return;
            }
            qms.put(c, qm);

            if (!quest.hasScriptRequirement(false)) {   // lack of scripted quest checks found thanks to Mali, Resinate
                log.debug("START quest {} disposed: Check.img/{}/0 has no startscript", questid, questid);
                qm.dispose();
                return;
            }

            ScriptEngine engine = getQuestScriptEngine(c, questid);
            if (engine == null) {
                log.warn("START Quest {} is uncoded.", questid);
                qm.dispose();
                return;
            }

            engine.put("qm", qm);

            Invocable iv = (Invocable) engine;
            scripts.put(c, iv);
            c.setClickedNPC();
            iv.invokeFunction("start", (byte) 1, (byte) 0, 0);
        } catch (final Throwable t) {
            log.error("Error starting quest script: {}", questid, t);
            dispose(c);
        }
    }

    public void start(Client c, byte mode, byte type, int selection) {
        Invocable iv = scripts.get(c);
        if (iv != null) {
            QuestActionManager invoked = qms.get(c);
            if (invoked != null) {
                invoked.resetDialogueSent();
            }
            try {
                c.setClickedNPC();
                iv.invokeFunction("start", mode, type, selection);
                disposeIfStalled(invoked, c);
            } catch (final Exception e) {
                log.error("Error starting quest script: {}", getQM(c).getQuest(), e);
                dispose(c);
            }
        }
    }

    public void end(Client c, short questid, int npc) {
        Quest quest = Quest.getInstance(questid);
        QuestStatus.Status status = c.getPlayer().getQuest(quest).getStatus();
        // containsNPC is a locked scan of every map object, so read it once and reuse it below.
        boolean npcOnMap = c.getPlayer().getMap().containsNPC(npc);
        if (!status.equals(QuestStatus.Status.STARTED) || (!npcOnMap && !quest.isAutoComplete())) {
            log.debug("END quest {} disposed: status {}, npc {} on map {} = {}, autoComplete {}", questid,
                    status, npc, c.getPlayer().getMapId(), npcOnMap, quest.isAutoComplete());
            dispose(c);
            return;
        }
        try {
            QuestActionManager qm = new QuestActionManager(c, questid, npc, false);
            if (qms.containsKey(c)) {
                log.debug("END quest {} ignored: {} already has quest script {} open", questid,
                        c.getPlayer().getName(), qms.get(c).getQuest());
                return;
            }
            if (!c.canClickNPC()) {
                log.debug("END quest {} ignored: {} is inside the 500ms npc-click cooldown", questid,
                        c.getPlayer().getName());
                return;
            }
            qms.put(c, qm);

            if (!quest.hasScriptRequirement(true)) {
                log.debug("END quest {} disposed: Check.img/{}/1 has no endscript", questid, questid);
                qm.dispose();
                return;
            }

            ScriptEngine engine = getQuestScriptEngine(c, questid);
            if (engine == null) {
                log.warn("END Quest {} is uncoded.", questid);
                qm.dispose();
                return;
            }

            engine.put("qm", qm);

            Invocable iv = (Invocable) engine;
            scripts.put(c, iv);
            c.setClickedNPC();
            iv.invokeFunction("end", (byte) 1, (byte) 0, 0);
        } catch (final Throwable t) {
            log.error("Error starting quest script: {}", questid, t);
            dispose(c);
        }
    }

    public void end(Client c, byte mode, byte type, int selection) {
        Invocable iv = scripts.get(c);
        if (iv != null) {
            QuestActionManager invoked = qms.get(c);
            if (invoked != null) {
                invoked.resetDialogueSent();
            }
            try {
                c.setClickedNPC();
                iv.invokeFunction("end", mode, type, selection);
                disposeIfStalled(invoked, c);
            } catch (final Exception e) {
                log.error("Error ending quest script: {}", getQM(c).getQuest(), e);
                dispose(c);
            }
        }
    }

    public void raiseOpen(Client c, short questid, int npc) {
        try {
            QuestActionManager qm = new QuestActionManager(c, questid, npc, true);
            if (qms.containsKey(c)) {
                return;
            }
            if (c.canClickNPC()) {
                qms.put(c, qm);

                ScriptEngine engine = getQuestScriptEngine(c, questid);
                if (engine == null) {
                    //FilePrinter.printError(FilePrinter.QUEST_UNCODED, "RAISE Quest " + questid + " is uncoded.");
                    qm.dispose();
                    return;
                }

                engine.put("qm", qm);

                Invocable iv = (Invocable) engine;
                scripts.put(c, iv);
                c.setClickedNPC();
                iv.invokeFunction("raiseOpen");
            }
        } catch (final Throwable t) {
            log.error("Error during quest script raiseOpen for quest: {}", questid, t);
            dispose(c);
        }
    }

    /**
     * Safety net for the dialogue-close lockout. Nearly every quest script guards with
     * {@code mode == -1 || (mode == 0 && type > 0)}, which does not catch the window X (mode 0,
     * type 0): the script decrements its status, matches no branch of its if/else-if chain and
     * returns having neither pushed a dialogue nor disposed. The session then sits in {@code qms}
     * forever, every later QUEST_ACTION dies at the {@code qms.containsKey(c)} check,
     * {@code NPCTalkHandler} refuses every conversation and quest updates never flush - only a map
     * change clears it.
     *
     * <p>An invocation that advanced no dialogue and did not dispose has fallen off its state
     * machine whatever {@code mode} meant, which is decidable here and not in the script. A
     * legitimate Prev pushes a dialogue and is untouched. The identity check keeps this off a
     * session some other path now owns.
     */
    private void disposeIfStalled(QuestActionManager invoked, Client c) {
        if (invoked != null && qms.get(c) == invoked && !invoked.isDialogueSent()) {
            log.debug("Quest {} script disposed: invocation pushed no dialogue and did not dispose",
                    invoked.getQuest());
            dispose(invoked, c);
        }
    }

    public void dispose(QuestActionManager qm, Client c) {
        qms.remove(c);
        scripts.remove(c);
        c.getPlayer().setNpcCooldown(System.currentTimeMillis());
        resetContext("quest/" + qm.getQuest() + ".js", c);
        c.getPlayer().flushDelayedUpdateQuests();
    }

    public void dispose(Client c) {
        QuestActionManager qm = qms.get(c);
        if (qm != null) {
            dispose(qm, c);
        }
    }

    public QuestActionManager getQM(Client c) {
        return qms.get(c);
    }

    public void reloadQuestScripts() {
        scripts.clear();
        qms.clear();
    }
}
