function enter(pi) {
    // Effect/Direction2.img/metro/Im is a Direction node (type/visual/start/x/y), the same shape as
    // the aranTutorial scenes showIntro already plays - not a Map/Effect.img path, so showEffect
    // (FIELD_EFFECT mode 3, which is what the dead showWZEffect name meant) would render nothing.
    pi.showIntro("Effect/Direction2.img/metro/Im");
    return true;
}