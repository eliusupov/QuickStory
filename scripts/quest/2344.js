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
 * Nothing here is invented. The end half copies scripts/quest/2300.js:97-102 verbatim - consume
 * the letter, 6,000 exp, then open 2312 - the identical body all eleven siblings carry, on a
 * quest with the identical lvmin/lvmax and the identical completion NPC. 2312's Check.img/0
 * carries no job list, so an Evan may hold it.
 */
function start(mode, type, selection) {
    if (mode != 1) {
        qm.dispose();
        return;
    }
    if (!qm.canHold(4032375, 1)) {
        qm.sendOk("Please have a slot available in your Etc inventory.");
        qm.dispose();
        return;
    }
    if (!qm.haveItem(4032375, 1)) {
        qm.gainItem(4032375, 1);
    }
    qm.forceStartQuest();
    qm.dispose();
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
