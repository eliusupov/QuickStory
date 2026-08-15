/*
	@Author Ronan
        (Neo Tokyo Teleporter)
*/

// v84 ticket 07: Year 2227 (683070400-683070402) enabled. It was authored here and commented
// out because the maps did not exist in the v83 client; they do now.
// `quests[i]` gates `array[i]`, so the new destination needed a seventh gate. 3749
// "Nibelung's Song" is the quest that closes the Year 2503 chain, which makes 2227 unlock on
// finishing the last previously-reachable area rather than alongside it. v84 ships NO quest of
// its own for 2227 (its new 37xx quests, 3756-3761, are the Crimson Sky/Dragon Rider chain),
// so this gate is a choice, not a port. Level fits: the 2227 maps carry lvLimit 120.
var quests = [3719, 3724, 3730, 3736, 3742, 3748, 3749];
var array = ["Year 2021 - Average Town Entrance", "Year 2099 - Midnight Harbor Entrance", "Year 2215 - Bombed City Center Retail District", "Year 2216 - Ruined City Intersection", "Year 2230 - Dangerous Tower Lobby", "Year 2503 - Air Battleship Bow", "Year 2227 - Dangerous City Intersection"];
var limit;

function start() {
    if (!cm.isQuestCompleted(3718)) {
        cm.sendOk("The time machine has not been activated yet.");
        cm.dispose();
        return;
    }

    for (limit = 0; limit < quests.length; limit++) {
        if (!cm.isQuestCompleted(quests[limit])) {
            break;
        }
    }

    if (limit == 0) {
        cm.sendOk("Prove your valor against the #bGuardian Nex#k before unlocking next Neo City maps.");
        cm.dispose();
        return;
    }

    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 0 && type > 0) {
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }

        if (status == 0) {
            var menuSel = generateSelectionMenu(array, limit);
            cm.sendSimple(menuSel);
        } else if (status == 1) {
            var mapid = 0;

            switch (selection) {
                case 0:
                    mapid = 240070100;
                    break;
                case 1:
                    mapid = 240070200;
                    break;
                case 2:
                    mapid = 240070300;
                    break;
                case 3:
                    mapid = 240070400;
                    break;
                case 4:
                    mapid = 240070500;
                    break;
                case 5:
                    mapid = 240070600;
                    break;
                case 6:
                    mapid = 683070400;
                    break;
            }

            if (mapid > 0) {
                cm.warp(mapid, 1);
            } else {
                cm.sendOk("Complete your mission first.");
            }
        }
    }
}

function generateSelectionMenu(array, limit) {     // nice tool for generating a string for the sendSimple functionality
    var menu = "";

    var len = Math.min(limit, array.length);
    for (var i = 0; i < len; i++) {
        menu += "#L" + i + "#" + array[i] + "#l\r\n";
    }
    return menu;
}

    