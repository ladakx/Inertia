package com.ladakx.inertia.files.config;

import com.ladakx.inertia.InertiaPlugin;
import com.ladakx.inertia.utils.MessageUtils;
import com.ladakx.inertia.utils.StringUtils;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public enum MessagesCFG {
    RELOAD_PLUGIN("Reload_Plugin"),
    NO_PERMISSIONS("No_Perm"),
    NOT_FOR_CONSOLE("Not_For_Console"),
    PLAYER_NOT_FOUND("Player_Not_Found"),
    WRONG_ARGS_COMMAND("Wrong_Args_Command"),
    DEBUG_RENDER_HITBOXES("Debug_Render_Hitboxes"),
    HELP_COMMAND("Help_Command"),
    HELP_COMMAND_ADMIN("Help_Command_Admin"),
    DEBUG_BLOCK_BB_TITLE("Debug_Block_Title"),
    DEBUG_BLOCK_BB_INFO("Debug_Block_Info"),
    DEBUG_BLOCK_NOT_FOUND("Debug_Block_Not_Found"),
    DEBUG_BLOCKS_SPAWNED("Debug_Blocks_Spawned"),
    DEBUG_BLOCKS_CLEARED("Debug_Blocks_Cleared"),
    NOT_FOR_THIS_VERSION("Not_For_This_Version"),
    NOT_FOR_THIS_WORLD("Not_For_This_World"),
    SELECT_REGION("Select_Region"),
    DEBUGSHAPE_CREATE_SUCCESS("DebugShape_Create_Success"),
    WORLDEDIT_NOT_ENABLED("WorldEdit_Not_Enabled"),
    COMMAND_DOES_NOT_EXIST("Command_Not_Found");

    final String path;
    final boolean isEmpty;
    List<Component> text;

    MessagesCFG(String path) {
        this.path = path;

        this.isEmpty = MessageUtils.isEmpty(path);
        if (this.isEmpty) {
            this.text = null;
            return;
        }

        this.text = MessageUtils.getMessage(path);
    }

    public String getPath() {
        return this.path;
    }

    public List<Component> getText() {
        return this.text;
    }

    public boolean isEmpty() {
        return this.isEmpty;
    }


    public void sendMessage(CommandSender sender) {
        sendMessage(InertiaPlugin.getAdventure().sender(sender));
    }

    public void sendMessage(CommandSender sender, String... replacement) {
        sendMessage(InertiaPlugin.getAdventure().sender(sender), replacement);
    }

    public void sendMessage(Player player) {
        sendMessage(InertiaPlugin.getAdventure().player(player));
    }

    public void sendMessage(Player player, String... replacement) {
        sendMessage(InertiaPlugin.getAdventure().player(player), replacement);
    }

    public void sendMessage(Audience aud) {
        if (this.isEmpty) {return;}

        for (Component component : this.text) {
            aud.sendMessage(component);
        }
    }

    public void sendMessage(Audience aud, String... replacement) {
        if (this.isEmpty) {return;}

        for (Component component : this.text) {
            aud.sendMessage(StringUtils.replace(component, replacement));
        }
    }


    public void refresh() {
        this.text = MessageUtils.getMessage(this.path);
    }

    public static void refreshAll() {
        for (MessagesCFG msg : values()) {
            msg.refresh();
        }
    }
}
