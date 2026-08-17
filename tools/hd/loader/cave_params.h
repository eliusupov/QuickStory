// Everything codecaves.h needs that is not a patch address, plus the runtime parameter
// assignment. Included by dllmain.cpp INSTEAD of codecaves.h -- it wraps it.
//
// codecaves.h is vendored from github.com/444Ro666/MapleEzorsia-v2 VERBATIM apart from
// the two v84 body edits marked in it. Its cave bodies read ~100 file-scope globals that
// Ezorsia assigns inside Client::UpdateResolution. Those assignments are reproduced in
// SetCaveParams() below, transcribed from Client.cpp in its own order, with
// Client::m_nGameWidth/Height replaced by the ini-driven g_w/g_h.
//
// WHY THIS IS NOT OPTIONAL: codecaves.h initialises myHeight/myWidth at STATIC INIT time
// (`int myHeight = -(Client::m_nGameHeight - 600) / 2;`), which runs before DllMain has
// read hd-res.ini. Leaving it at that would make every cave lay out for 800x600 no
// matter what the ini says. SetCaveParams() must run after the ini read and before
// ApplyAll(). Ezorsia has the same ordering requirement and meets it the same way.
#pragma once

// Shim for the two Client statics codecaves.h's static initialisers reference. Their
// values here are irrelevant -- SetCaveParams() overwrites everything derived from them.
struct Client { static int m_nGameWidth, m_nGameHeight; };
int Client::m_nGameWidth = 800;
int Client::m_nGameHeight = 600;

// Return addresses for cave bodies present in codecaves.h that our patch set never
// applies -- commented-out experiments, and the group A/B/L caves that edits\ owns.
// Nothing writes a jmp to those bodies, so they are unreachable; 0 keeps them compiling.
static const unsigned long dwBossBarRetn = 0, dwBossBarRetn2 = 0,
    dwCUIStatusBarChatLogAddBypassRetn = 0, dwCUIStatusBarChatLogAddBypass2Retn = 0,
    dwCashFixOnOffCall = 0, dwCashFixRtm = 0, dwLoginBackBtnFixRetn = 0,
    dwLoginCreateDlgRtn = 0, dwLoginPasswordRtn = 0, dwLoginUsernameRtn = 0,
    dwMuruengraidTimerCanvasRetn = 0, dwMuruengraidTimerMinutesRetn = 0,
    dwMuruengraidTimerSecondsRetn = 0, dwStatsSubMovRetn = 0, dwloginFrameFixCall = 0;

#include "codecaves.h"

// Ezorsia's own tuning knobs, from Client.cpp. dojangYoffset/customEngX/customEngY are
// locals at Client.cpp:567; msgAmnt is the MsgAmount ini setting at Client.cpp:362.
static void SetCaveParams(int g_w, int g_h) {
    const int dojangYoffset = 0, customEngX = -22, customEngY = -62;
    const int msgAmnt = 6;   // Ezorsia's MsgAmount default; matches MoreGainMsgsOffset

    nStatusBarY = g_h - 578;                                    // Client.cpp:90
    MoreGainMsgsOffset = msgAmnt;                               // Client.cpp:371
    MoreGainMsgsFadeOffset = 15000;                             // Client.cpp:373
    MoreGainMsgsFade1Offset = 255 * 4 / 3;                      // Client.cpp:375
    myHeight = (g_h - 600) / 2;                                 // Client.cpp:501
    myWidth = (g_w - 800) / 2;                                  // Client.cpp:502
    nHeightOfsetted1 = 316; nWidthOfsetted1 = 256;
    nTopOfsetted1 = 0 + myHeight; nLeftOfsetted1 = 0 + myWidth;         // :503
    nHeightOfsetted2 = 104; nWidthOfsetted2 = 256;
    nTopOfsetted2 = 318 + myHeight; nLeftOfsetted2 = -1 + myWidth;      // :505
    nHeightOfsetted3 = 163; nWidthOfsetted3 = 246;
    nTopOfsetted3 = 426 + myHeight; nLeftOfsetted3 = 0 + myWidth;       // :507
    nHeightOfsetted4 = 78; nWidthOfsetted4 = 508;
    nTopOfsetted4 = 17 + myHeight; nLeftOfsetted4 = 272 + myWidth;      // :509
    nHeightOfsetted5 = 430; nWidthOfsetted5 = 412;
    nTopOfsetted5 = 95 + myHeight; nLeftOfsetted5 = 275 + myWidth;      // :511
    nHeightOfsetted6 = 358; nWidthOfsetted6 = 90;
    nTopOfsetted6 = 157 + myHeight;                                     // :513
    nHeightOfsetted7 = 56; nWidthOfsetted7 = 545;
    nTopOfsetted7 = 530 + myHeight; nLeftOfsetted7 = 254 + myWidth;     // :516
    nHeightOfsetted8 = 22; nWidthOfsetted8 = 89;
    nTopOfsetted8 = 97 + myHeight; nLeftOfsetted8 = 690 + myWidth;      // :518
    nHeightOfsettedPrev = 165 + myHeight; nWidthOfsettedPrev = 212 + myWidth;
    nTopOfsettedPrev = 40 + myHeight; nLeftOfsettedPrev = 24 + myWidth;  // :522
    myAlwaysViewRestoreFixOffset = myHeight;                    // Client.cpp:530
    nTopOfsettedVerFix = 10 + myHeight; nLeftOfsettedVerFix = 645 + myWidth;  // :545
    nHeightOfsettedLoginBackCanvasFix = 352 + myHeight;
    nWidthOfsettedLoginBackCanvasFix = 125 + myWidth;                   // :550
    nTopOfsettedLoginBackCanvasFix = 125 + myHeight;
    nLeftOfsettedLoginBackCanvasFix = 0 + myWidth;                      // :551
    nHeightOfsettedLoginViewRecFix = 167 + myHeight;
    nWidthOfsettedLoginViewRecFix = 113 + myWidth;                      // :557
    nTopOfsettedLoginViewRecFix = 51 + myHeight;
    nLeftOfsettedLoginViewRecFix = 0 + myWidth;                         // :558
    a1x = 0 + myWidth; a2x = -149 + myWidth; a2y = 0 + myHeight;
    a3 = 25; a1y = -250;                                                // :561
    yOffsetOfMuruengraidPlayer = 50 + dojangYoffset;
    xOffsetOfMuruengraidPlayer = 169 + myWidth;                         // :568
    yOffsetOfMuruengraidClock = 26 + dojangYoffset;
    xOffsetOfMuruengraidClock = 400 + myWidth;                          // :570
    yOffsetOfMuruengraidMonster = 50 + dojangYoffset;
    xOffsetOfMuruengraidMonster = 631 + myWidth;                        // :572
    yOffsetOfMuruengraidMonster1 = 32 + dojangYoffset;
    xOffsetOfMuruengraidMonster1 = 317 + myWidth;                       // :574
    yOffsetOfMuruengraidMonster2 = 32 + dojangYoffset;
    xOffsetOfMuruengraidMonster2 = 482 + myWidth;                       // :576
    yOffsetOfMuruengraidEngBar = 86 + dojangYoffset + customEngY;
    xOffsetOfMuruengraidEngBar = 17 + myWidth + customEngX;             // :578
    yOffsetOfMuruengraidEngBar1 = 130 + dojangYoffset + customEngY;
    xOffsetOfMuruengraidEngBar1 = 20 + myWidth + customEngX;            // :580
    yOffsetOfMuruengraidEngBar2 = 80 + dojangYoffset + customEngY;
    xOffsetOfMuruengraidEngBar2 = 9 + myWidth + customEngX;             // :582
    yOffsetOfMuruengraidClearRoundUI = 260 + myHeight;
    xOffsetOfMuruengraidClearRoundUI = 400 + myWidth;                   // :584
    yOffsetOfMuruengraidTimerBar = 16 + dojangYoffset;
    xOffsetOfMuruengraidTimerBar = 345 + myWidth;                       // :592
    xOffsetOfMuruengraidMonster1_2 = 318 + myWidth;                     // :594
}
