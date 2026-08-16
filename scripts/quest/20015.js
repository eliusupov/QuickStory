/*
	NPC Name:		Empress Cygnus (1101000)
	Map(s):			Ereve : Empress's Palace (130000000)
	Description:		Quest - Greetings From the Young Empress
	Quest ID:		20015

	Check.img/20015/0 declares startscript q20015s and Check.img/20015/1 is empty, so only
	start() is ever routed here. Act.img/20015/0 and /1 are both empty - this quest awards
	nothing, it is the dialogue gate that closes the Noblesse tutorial (20000-20008, 20100)
	and hands the player to Neinheart, whose 20016.js already exists. Without this file
	QuestScriptManager.start disposes and the chain dead-ends in silence.

	mode != 1 always disposes, so the window X (mode 0, type 0) cannot wedge the session.
*/

var status = -1;

function start(mode, type, selection) {
    if (mode != 1) {    // -1 escape, 0 = Decline / Prev / window X - never fall through undisposed
        qm.dispose();
        return;
    }

    status++;
    if (status == 0) {
        qm.sendNext("So you are the one who answered my call... Thank you for becoming one of my Knights. I hope you will stay by my side for a long time.");
    } else if (status == 1) {
        qm.sendNext("My tactician, #b#p1101002##k, will help you become a competent Knight. Go and speak with him, and do not be discouraged by what he tells you.");
    } else if (status == 2) {
        qm.forceStartQuest();
        qm.forceCompleteQuest();
        qm.sendNext("#p1101002# will not appoint you as a Knight-in-Training until you are strong enough. Take the training course he has prepared, then go see #p1102000#, the training instructor.");
    } else {
        qm.dispose();
    }
}

function end(mode, type, selection) {
    qm.dispose();   // Check.img/20015/1 declares no endscript; unreachable, but never leave qms held
}
