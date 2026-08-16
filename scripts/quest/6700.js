/*
	NPC Name:		Athena Pierce (1012100)
	Map(s):			Henesys : Bowman Instructional School (100000201)
	Description:		Quest - The Bowman's Road
	Quest ID:		6700

	Check.img/6700/0 has no startscript - the quest auto-starts on entering field 100000200
	for job 300 at Lv. 10-30. Only Check.img/6700/1 declares a script (endscript q6700e), so
	only end() is ever routed here. Without this file the Complete click disposed silently.

	Dialogue is Say.img/6700/1 verbatim ("0" then the "yes" branch).
	NO REWARD IS GRANTED: Act.img/6700/1 is empty in this Quest.wz, so the data awards
	nothing. Do not add items here without data to back them.

	mode != 1 always disposes, so the window X (mode 0, type 0) cannot wedge the session.
*/

var status = -1;

function end(mode, type, selection) {
    if (mode != 1) {    // -1 escape, 0 = No / Prev / window X - never fall through undisposed
        qm.dispose();
        return;
    }

    status++;
    if (status == 0) {
        qm.sendYesNo("These are for you. Choose anything you need.");
    } else if (status == 1) {
        qm.forceCompleteQuest();
        qm.sendOk("Please be a excellent bowman. Take care.");
    } else {
        qm.dispose();
    }
}

function start(mode, type, selection) {
    qm.dispose();   // Check.img/6700/0 declares no startscript; unreachable, but never leave qms held
}
