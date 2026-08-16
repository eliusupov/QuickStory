/*
	NPC Name:		Captain Al (1002103)
	Map(s):			Lith Harbor (104000000), Orbis Park (200000200)
	Description:		Quest - Raise the Rep! (Captain Al's Passing of Knowledge, step 2)
	Quest ID:		2233

	Only Check.img/2233/1 declares a script (endscript q2233e); the start is data-driven.
	Same shape as the step-1 script 2232.js, which reads the family entry directly because
	the Rep goal has no representation in Check.img/2233/1 - that node carries the endscript
	and nothing else, so the gate has to live here.

	Goal per QuestInfo.img/2233/1: "Achieve 1,000 Rep". Step 3 (2234) spells out that the
	Family window shows current Rep / total Rep, and asks for a *total*, so this reads
	getTotalReputation().

	NO REWARD IS GRANTED: Act.img/2233/0 and /1 are both empty. (2232.js hands out 3000 exp
	that its own Act node does not contain - that number is not data-backed, so it is not
	copied here.)

	Disposes on every path, so the window X cannot wedge the session.
*/

function end(mode, type, selection) {
    var familyEntry = qm.getPlayer().getFamilyEntry();
    if (familyEntry != null && familyEntry.getTotalReputation() >= 1000) {
        qm.forceCompleteQuest();
        qm.sendNext("1,000 Rep! Seeing your Rep climb like that never ceases to surprise me. Your Juniors must think the world of you.");
    } else {
        qm.sendNext("You are not at 1,000 Rep yet. Help your Juniors gain EXP and level up - that is the only way there.");
    }
    qm.dispose();
}

function start(mode, type, selection) {
    qm.dispose();   // Check.img/2233/0 declares no startscript; unreachable, but never leave qms held
}
