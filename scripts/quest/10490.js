/* Quest 10490 - Evan launch event, KMS quest v84 ships with an untranslated Korean name
 * (QuestInfo.img/10490/name). Cassandra (9010010), Lv.13+, repeatable every 30 min.
 *
 * Act.img/10490 is empty on both sides. The completion side carries infoNumber 10490 with
 * infoex value "99999", i.e. an event counter no data in this tree produces - so the quest
 * starts but cannot be completed. That is what v84 ships; nothing here invents a counter.
 */
var status = -1;

function start(mode, type, selection) {
    if (mode == -1 || (mode == 0 && type > 0)) {
        qm.dispose();
        return;
    }
    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    if (status == 0) {
        qm.sendNext("Ahhh! I'm so bored! Is there anything fun do to? No? In that case, I have a plan. Wanna listen?");
    } else if (status == 1) {
        qm.forceStartQuest();
        qm.dispose();
    }
}
