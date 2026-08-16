// Portal "out00" of 900030000 ("Afrien's Memory" / "Behind the Stronghold").
// Effect.wz has no Direction node named outAfrienMemory, and the portal's own tm is
// 999999999, so the script has to supply the destination or the portal is dead - a portal
// with a script name never falls back to tm/tn (GenericPortal.enterPortal).
// String.wz/ToolTipHelp.img/PortalTooltip/900030000/out00 calls the far side the "Black
// Magician Expedition Force Stronghold", but no such map exists in this WZ, so fall back on
// the map's own returnMap/forcedReturn, 914100021.
function enter(pi) {
    pi.playPortalSound();
    pi.warp(914100021, 0);
    return true;
}