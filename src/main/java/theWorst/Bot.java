package theWorst;

import arc.Events;
import mindustry.entities.type.Player;
import mindustry.game.EventType;
import org.javacord.api.DiscordApi;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import theWorst.database.Database;
import theWorst.database.PlayerD;
import theWorst.database.Setting;
import theWorst.discord.BotConfig;
import theWorst.discord.Command;
import theWorst.discord.DiscordCommands;

import java.util.Collections;
import java.util.HashMap;

import static mindustry.Vars.playerGroup;
import static mindustry.Vars.world;
import static theWorst.Tools.Commands.isCommandRelated;
import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.Formatting.*;
import static theWorst.Tools.Json.loadJson;
import static theWorst.Tools.Json.saveJson;
import static theWorst.Tools.Maps.hasMapAttached;

public class Bot {
    public static String dir = Config.configDir + "bot/";
    static final String restrictionFile = dir + "restrictions.json";
    public static BotConfig config;
    public static DiscordApi api = null;
    public static final HashMap<Long,LinkData> pendingLinks = new HashMap<>();
    private static final DiscordCommands handler = new DiscordCommands();
    private static final BotCommands commands = new BotCommands(handler);

    public static class LinkData{
        public String name,pin,id;
        LinkData(String name,String pin,String id){
            this.name=name;
            this.pin=pin;
            this.id=id;
        }
    }

    public Bot(){
        Events.on(EventType.PlayerChatEvent.class,e->{
            if(isCommandRelated(e.message)) return;
            sendToLinkedChat("**"+cleanName(e.player.name)+"** : "+e.message.substring(e.message.indexOf("]")+1));
        });

        Events.on(EventType.PlayEvent.class, e->{
           sendToLinkedChat("===*Playing on " + world.getMap().name() + "*===");
        });


        Events.on(EventType.PlayerChatEvent.class,e->{
            if(api == null || !config.channels.containsKey("commandLog")) return;
            if(!isCommandRelated(e.message)) return;
            PlayerD pd = Database.getData(e.player);
            config.channels.get("commandLog").sendMessage(String.format("**%s** - %s (%d): %s",
                    cleanColors(pd.originalName), pd.rank, pd.serverId, e.message));
        });
        loadRestrictions();
        connect();
    }

    public static void sendToLinkedChat(String message){
        if(api == null || !config.channels.containsKey("linked")) return;
        config.channels.get("linked").sendMessage(message);
    }

    public static void onRankChange(String name, long serverId, String prev, String now, String by, String reason) {
        if(!config.channels.containsKey("log")) return;
        config.channels.get("log").sendMessage(String.format("**%s** (%d) **%s** -> **%s** \n**by:** %s \n**reason:** %s",
                name,serverId,prev,now,by,reason));
    }

    public static void connect(){
        disconnect();
        config = new BotConfig();
        if(api==null) return;
        api.addMessageCreateListener(handler);

        api.addMessageCreateListener((event)->{
            if(!config.channels.containsKey("linked") || event.getChannel() != config.channels.get("linked")) return;
            String content = cleanEmotes(event.getMessageContent());
            if(event.getMessageAuthor().isBotUser() || content.startsWith(config.prefix)) return;
            //if there wos only emote in message it got removed and we don't want to show blank message
            if(content.replace(" ","").isEmpty()) return;
            for(Player p : playerGroup) {
                if(Database.hasEnabled(p, Setting.chat)) {
                    p.sendMessage("[coral][[[royal]"+event.getMessageAuthor().getName()+"[]]:[sky]"+content);
                }
            }
        });

        api.addMessageCreateListener((event)->{
            if(event.getMessageAuthor().isBotUser()) return;
            if(hasMapAttached(event.getMessage()) && !handler.hasCommand(event.getMessageContent().replace(config.prefix,""))){
                event.getChannel().sendMessage("If you want to post map use !postmap command!");
                event.getMessage().delete();
            }
        });
    }

    public static void loadRestrictions() {
        loadJson(restrictionFile, data -> {
            for (Object o : data.keySet()) {
                String s = (String) o;
                JSONArray ja = (JSONArray) data.get(o);
                String[] roles = new String[ja.size()];
                for (int i = 0; i < roles.length; i++) {
                    roles[i] = (String) ja.get(i);
                }
                if (!handler.commands.containsKey(s)) continue;
                handler.commands.get(s).role = roles;
            }
        }, () -> {
            JSONObject data = new JSONObject();
            for (Command c : handler.commands.values()) {
                JSONArray roles = new JSONArray();
                if(c.role != null) {
                    Collections.addAll(roles, c.role);
                }
                data.put(c.name, roles);

            }
            saveJson(restrictionFile, data.toJSONString());
            logInfo("files-default-config-created", "command restrictions", restrictionFile);
        });
    }

    public static boolean disconnect(){
        if(api == null) return false;
        api.disconnect();
        return true;
    }
}
