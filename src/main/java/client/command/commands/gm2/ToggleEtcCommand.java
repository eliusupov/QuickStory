package client.command.commands.gm2;

import client.Character;
import client.Client;
import client.command.Command;

public class ToggleEtcCommand extends Command {

    @Override
    public void execute(Client c, String[] params) {
        Character chr = c.getPlayer();
        boolean newStatus = !chr.isEtcDropEnabled();
        chr.setEtcDropEnabled(newStatus);
        chr.dropMessage(5, "ETC drops are now " + (newStatus ? "enabled" : "disabled") + " for your account.");
    }

    @Override
    public String getDescription() {
        return "Toggle ETC drops on or off for your account. Syntax: !toggleetc";
    }

    @Override
    public int getRank() {
        return 2;
    }
}
