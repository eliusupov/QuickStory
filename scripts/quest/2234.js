/*
	NPC Name:		Captain Al (1002103)
	Map(s):			Lith Harbor (104000000), Orbis Park (200000200)
	Description:		Quest - Enjoy the Entitlement! (Captain Al's Passing of Knowledge, step 3)
	Quest ID:		2234

	Only Check.img/2234/1 declares a script (endscript q2234e); the start is data-driven.
	Goal per QuestInfo.img/2234/1, verbatim: "Total of at least 2,000 Rep / Current Rep under
	500" - hence getTotalReputation() >= 2000 && getReputation() < 500.

	NO REWARD IS GRANTED: Act.img/2234/0 and /1 are both empty.

	Disposes on every path, so the window X cannot wedge the session.
*/

function end(mode, type, selection) {
    var familyEntry = qm.getPlayer().getFamilyEntry();
    if (familyEntry != null && familyEntry.getTotalReputation() >= 2000 && familyEntry.getReputation() < 500) {
        qm.forceCompleteQuest();
        qm.sendNext("You have the looks of a great Senior. Never forget that supporting your Juniors, and everyone in the Family, is what all of this rests on. Go start a big Family of your own!");
    } else {
        qm.sendNext("Not yet. I want a #rtotal Rep of 2,000 or above#k, and your #rcurrent Rep down below 500#k. Spend it - the Entitlement is the whole point.");
    }
    qm.dispose();
}

function start(mode, type, selection) {
    qm.dispose();   // Check.img/2234/0 declares no startscript; unreachable, but never leave qms held
}
