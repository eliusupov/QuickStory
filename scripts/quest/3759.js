/* Quest 3759 - "Towards the Sky 2" (v84, ticket 09). Crimson Sky chain, 3756 -> 3761.
 *
 * This is the quest that grants SOARING - the skill ticket 06's Crimson Sky maps gate on
 * (info/needSkillForFly=1). Quest.wz Act.img/3759/1 declares: exp 11000, remove 1 #t4032531#,
 * and skill level 1 / master 1 on one of 1026 / 10001026 / 20001026 / 20011026 depending on job.
 * The four job arrays in the WZ are: explorers (0-522) -> 1026, Cygnus (1000-1512) -> 10001026,
 * Aran (2100-2112) -> 20001026, Evan (2001, 2200-2218) -> 20011026.
 *
 * 20011026 lives in Skill.wz/2001.img. Ticket 09 wrote the Evan branch as a dropMessage because
 * that image was unmerged; ticket 10 merged it, so all four variants now resolve and the branch
 * is a plain teachSkill like the other three.
 *
 * Check.img/3759/0/end is "2000010100" in v84 itself - an already-expired date. Cosmic's
 * EndDateRequirement compares it against the wall clock, so the whole 3756-3761 chain cannot be
 * accepted until an owner drops that node. See docs/wz-baseline/merge-lists/09/DEEP-ROWS.md.
 */
var status = -1;

function end(mode, type, selection) {
    if (mode != 1) {   // no sendNextPrev here, so "back" is unreachable - anything but OK ends it
        qm.dispose();
        return;
    }
    status++;

    if (status == 0) {
        qm.sendNext("Okay. If you're ready, I'll finish up the potion and sprinkle it on you. Then, you'll be able to fly.");
    } else if (status == 1) {
        var jid = qm.getPlayer().getJob().getId();
        var soaring;
        if (jid == 2001 || (jid >= 2200 && jid <= 2218)) {
            soaring = 20011026;
        } else if (jid >= 2100 && jid <= 2112) {
            soaring = 20001026;
        } else if (jid >= 1000 && jid <= 1512) {
            soaring = 10001026;
        } else {
            soaring = 1026;
        }

        qm.teachSkill(soaring, 1, 1, -1);

        qm.gainItem(4032531, -1);
        qm.gainExp(11000);
        qm.forceCompleteQuest();
        qm.sendNext("Now you can fly! One word of caution, the Soaring skill can only be used in the Crimson Sky area, including the <Crimson Sky Dock>. Also, your MP will be continuously depleted while you're flying, so keep an eye on your MP bar. Good luck!");
        qm.dispose();
    }
}
