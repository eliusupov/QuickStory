/*
    Map 130030006 (Empress's Road : Small Bridge), trigger portal scr00.
    Last of the Cygnus tutorial hint triggers: tutorHelper on 130030000 spawns Mimo,
    tutorMinimap on 130030001 fires guideHint(1), and this one is the world map lesson.
    UI.wz/tutorial.img/26 is the world map page and 25 the minimap page - the same pair the
    explorer tutorial uses from scripts/portal/infoWorldmap.js and infoMinimap.js on 50000.
    blockPortal keeps it from re-firing every time the player walks back over the bridge.
*/
function enter(pi) {
    pi.showInfo("UI/tutorial.img/26");
    pi.blockPortal();
    return true;
}
