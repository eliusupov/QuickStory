/* @Author Ronan
 * @Author Vcoc
        Name: Steward
        Map(s): Foyer
        Info: Commands
        Script: commands.js
*/

var status;

var common_heading = "@";
var staff_heading = "!";

var levels = ["Common", "Donator", "JrGM", "GM", "SuperGM", "Developer", "Admin"];
var commands;
var rank;
var page;
var page_size = 10;
var back_to_ranks = 10;
var previous_page = 11;
var next_page = 12;

function writeHeavenMSCommands() {
    const CommandsExecutor = Java.type('client.command.CommandsExecutor');
    commands = CommandsExecutor.getInstance().getGmCommands();
}

function start() {
    writeHeavenMSCommands();
    status = 0;
    showRanks();
}

function showRanks() {
    var sendStr = "There are all available commands for you:\r\n\r\n#b";
    for (var i = 0; i <= cm.getPlayer().gmLevel(); i++) {
        sendStr += "#L" + i + "#" + levels[i] + "#l\r\n";
    }

    cm.sendSimple(sendStr);
}

function showCommands() {
    var lvComm = commands.get(rank).getLeft();
    var lvDesc = commands.get(rank).getRight();
    var lvHead = (rank < 2) ? common_heading : staff_heading;
    var first = page * page_size;
    var last = Math.min(first + page_size, lvComm.size());
    var sendStr = "The following commands are available for #b" + levels[rank] + "#k:\r\n\r\n";

    for (var i = first; i < last; i++) {
        sendStr += "  #L" + (i - first) + "# " + lvHead + lvComm.get(i) + " - " + lvDesc.get(i) + "#l\r\n";
    }

    sendStr += "\r\n#L" + back_to_ranks + "#Back to ranks#l\r\n";
    if (page > 0) {
        sendStr += "#L" + previous_page + "#Previous page#l\r\n";
    }
    if (last < lvComm.size()) {
        sendStr += "#L" + next_page + "#Next page#l\r\n";
    }
    cm.sendSimple(sendStr);
}

function action(mode, type, selection) {
    if (mode == -1 || (mode == 0 && type > 0)) {
        cm.dispose();
        return;
    }

    if (status == 0) {
        if (mode != 1) {
            cm.dispose();
            return;
        }
        rank = Math.max(0, Math.min(selection, cm.getPlayer().gmLevel()));
        page = 0;
        status = 1;
        showCommands();
        return;
    }

    if (selection == back_to_ranks) {
        status = 0;
        showRanks();
    } else if (selection == previous_page && page > 0) {
        page--;
        showCommands();
    } else if (selection == next_page && (page + 1) * page_size < commands.get(rank).getLeft().size()) {
        page++;
        showCommands();
    } else {
        showCommands();
    }
}
