function enter(pi) {
    // Evan's Abandoned Cave (910050300) hangs off this same portal. 105070300/in00 is pt=8, a
    // SERVER-scripted portal - the client data carries a script name and no destination - so one
    // name serving two destinations is the normal shape here, not a collision. This file already
    // branches; the cave is a third arm.
    // Why this map and this portal: 910050300's returnMap AND forcedReturn are both 105070300, and
    // 910050300 appears in no other map's `tm` across all 5337 Map.wz images and in no script.
    // Npc 1063018 lives alone on it and is required by eight quests - 22549's hand-in through
    // 22566 - so without this the Evan chain is dead from 22550 to 22596.
    // Ordered first deliberately: 22549 is Evan-only, so this can never be true for the Aran or
    // thief players the branch below serves.
    if (pi.isQuestStarted(22549) || pi.isQuestCompleted(22549)) {
        pi.playPortalSound();
        pi.warp(910050300, 0);
        return true;
    }

    if (pi.isQuestCompleted(20730) || pi.isQuestCompleted(21734)) {  // puppeteer defeated, newfound secret path
        pi.playPortalSound();
        pi.warp(105040201, 2);
        return true;
    }

    pi.openNpc(1063011, "PupeteerPassword");
    return false;
}