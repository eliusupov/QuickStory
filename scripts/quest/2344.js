/* Quest 2344 - "Mushking Empire in Danger", the EVAN copy of 2300-2310.
 *
 * Check.img/2344/0/job is 2210-2218 (EVAN2..EVAN10, Job.java:62-63), lvmin 30 / lvmax 38, start
 * at Mike (1040001); Check.img/2344/1 wants 1x #t4032375# (Recommendation Letter - Job Instructor)
 * handed to the Head Security Officer (1300005). Act.img/2344 is EMPTY on both sides.
 *
 * The letter has exactly one source in the whole tree: the start half of the eleven sibling
 * quests 2300-2310, which hand it out from script because Act is empty for them too
 * (scripts/quest/2300.js:48-62). This file used to move the quest state and nothing else, which
 * left the Evan copy uncompletable - accepted, then permanently stuck on an item nothing could
 * give. QuestInfo/2344/1 is v84's own instruction and says so outright: "Take the
 * #bRecommendation Letter#k that Mike has written and go to #b#p1300005##k".
 *
 * Nothing here is invented. Both halves copy scripts/quest/2300.js verbatim - the same start
 * conversation that hands out the letter AND offers the ride to the Mushking Empire
 * (sendYesNo -> qm.warp(106020000), 2300.js:44-56), and the same end (consume the letter,
 * 6,000 exp, open 2312). The Evan copy previously handed out the letter but never offered the
 * teleport, so an Evan accepted the quest, got the letter, and was left standing in Henesys with
 * no way in - the conversation ended after the first prompt. 2312's Check.img/0 carries no job
 * list, so an Evan may hold it.
 */
var status = -1;

function start(mode, type, selection) {
    if (mode == -1) {
        qm.dispose();
    } else {
        if (mode == 0 && type > 0) {
            if (status != 3) {
                qm.sendOk("Really? It's an urgent matter, so if you have some time, please see me.");
                qm.dispose();
            } else {
                if (qm.canHold(4032375, 1)) {
                    qm.sendNext("Okay. In that case, I'll just give you the routes to the Mushking Empire. #bNear the west entrance of Henesys,#k you'll find an #bempty house#k. Enter the house, and turn left to enter#b<Themed Dungeon : Mushroom Castle>#k. That's the entrance to the Mushking Empire. There's not much time!");
                } else {
                    qm.sendOk("Please have a slot available in your Etc inventory.");
                    qm.dispose();
                }
            }

            status++;
        } else {
            if (mode == 1) {
                status++;
            } else {
                status--;
            }

            if (status == 0) {
                qm.sendAcceptDecline("Now that you have made the job advancement, you look like you're ready for this. I have something I'd like to ask you for help. Are you willing to listen?");
            } else if (status == 1) {
                qm.sendNext("What happened is that the #bMushking Empire#k is currently in disarray. The Mushking Empire is located near Henesys, featuring the peace-loving, intelligent King Mush. Recently, he began to feel ill, so he decided to appoint his only daughter #bPrincess Violetta#k. Something must have happened since then for the empire to be in its current state.");
            } else if (status == 2) {
                qm.sendNext("I am not aware of the exact details, but it's obvious something terrible had taken place, so I think it'll be better if you go there and assess the damage yourself. An explorer like you seem more than capable of saving the Mushking Empire. I have just written you a #brecommendation letter#k, so I suggest you head over to the Mushking Empire immediately and look for the #bHead Security Officer#k.\r\n\r\n#fUI/UIWindow.img/QuestIcon/4/0#\r\n#v4032375# #t4032375#");
            } else if (status == 3) {
                qm.sendYesNo("By the way, do you know where the Mushking Empire is located? It'll be okay if you can find your way there, but if you don't mind, I can take you straight to the entrance.");
            } else if (status == 4) {
                if (qm.canHold(4032375, 1)) {
                    if (!qm.haveItem(4032375, 1)) {
                        qm.gainItem(4032375, 1);
                    }

                    qm.warp(106020000, 0);
                    qm.forceStartQuest();
                } else {
                    qm.sendOk("Please have a slot available in your Etc inventory.");
                }

                qm.dispose();

            } else if (status == 5) {
                if (!qm.haveItem(4032375, 1)) {
                    qm.gainItem(4032375, 1);
                }

                qm.forceStartQuest();
                qm.dispose();

            }
        }
    }
}

function end(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    if (!qm.haveItem(4032375, 1)) {
        qm.sendOk("What do you want, hmmm?");
        qm.dispose();
        return;
    }
    qm.gainItem(4032375, -1);
    qm.gainExp(6000);
    qm.forceCompleteQuest();
    qm.forceStartQuest(2312);
    qm.dispose();
}
