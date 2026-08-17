/* Perion Warning Post (1022107)
    Quest 22530 "A Guard's Third Assignment: Maintaining Warning Signs".

    Trigger is stated verbatim in Quest.wz/Say.img/22530/0/yes/0: "go find the five Warning
    Signs located in the section I mentioned and click on them to read them". The five signs
    stand on 101030000/101030100/101030200/101030300/101030400 (Map.wz life, v84).

    Quest.wz/Check.img/22530/1 completes only when record 22597 reads exactly "5", so a sign
    must never count twice. Dedupe by map id via QuestStatus.addMedalMap, the same way
    MapScriptMethods.explorerQuest solves this shape - it is persisted in the medalmaps table.
 */

function start() {
    if (!cm.isQuestStarted(22530)) {
        cm.sendOk("A warning sign listing the monsters that roam this area.");
        cm.dispose();
        return;
    }

    var qs = cm.getQuestRecord(22530);
    if (qs.addMedalMap(cm.getMapId())) {
        cm.setQuestProgress(22530, 22597, qs.getMedalProgress());
    }

    cm.sendOk("You wrote down the monsters displayed on this Warning Sign. (#b" + qs.getMedalProgress() + "/5#k)");
    cm.dispose();
}
