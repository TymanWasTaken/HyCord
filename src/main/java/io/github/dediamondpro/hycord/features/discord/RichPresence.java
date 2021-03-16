package io.github.dediamondpro.hycord.features.discord;

import club.sk1er.mods.core.util.MinecraftUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jcm.discordgamesdk.Core;
import de.jcm.discordgamesdk.CreateParams;
import de.jcm.discordgamesdk.LogLevel;
import de.jcm.discordgamesdk.activity.Activity;
import io.github.dediamondpro.hycord.core.StartCore;
import io.github.dediamondpro.hycord.core.Utils;
import io.github.dediamondpro.hycord.options.settings;
import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.time.Instant;
import java.util.Locale;

public class RichPresence {
    int ticks;
    Instant time;
    int partyMembers = 1;
    boolean partyLeader = true;
    boolean checkedLoc = false;
    String secondLine = "In a party";

    @SubscribeEvent
    void onTick(TickEvent.ClientTickEvent event) {
        ticks++;
        if (ticks % 100 != 0 || !Utils.isHypixel() || Minecraft.getMinecraft().theWorld == null && Minecraft.getMinecraft().thePlayer == null || !settings.enableRP)
            return;
        if(!checkedLoc){
            Minecraft.getMinecraft().thePlayer.sendChatMessage("/locraw");
        }
        Scoreboard scoreboard = Minecraft.getMinecraft().theWorld.getScoreboard();
        ScoreObjective sidebarObjective = scoreboard.getObjectiveInDisplaySlot(1);
        if (sidebarObjective != null) {
            String objectiveName = sidebarObjective.getDisplayName().replaceAll("(?i)\\u00A7.", "");
            try {
                updateRPC(objectiveName.substring(0, 1).toUpperCase() + objectiveName.substring(1).toLowerCase(Locale.ROOT));
            } catch (IOException err) {
                FMLLog.getLogger().log(Level.ERROR, err);
            }
        }
    }

    @SubscribeEvent
    void worldLoad(WorldEvent.Load event) {
        time = Instant.now();
        checkedLoc = false;
    }

    @SubscribeEvent
    void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (MinecraftUtils.isHypixel()) {
            try (CreateParams params = new CreateParams()) {
                params.setClientID(819625966627192864L);
                params.setFlags(CreateParams.getDefaultFlags());
                StartCore.core = new Core(params);
                StartCore.core.setLogHook(LogLevel.DEBUG, (level, message) -> {
                    Level level1 = Level.INFO;
                    switch (level) {
                        case ERROR:
                            level1 = Level.ERROR;
                            break;
                        case WARN:
                            level1 = Level.WARN;
                            break;
                        case INFO:
                            level1 = Level.INFO;
                            break;
                        case DEBUG:
                            level1 = Level.DEBUG;
                            break;
                    }
                    FMLLog.getLogger().log(level1, message);
                });
                StartCore.isEnabled = true;
                Thread callbacks = new Thread(() ->{
                        while (StartCore.isEnabled) {
                        StartCore.core.runCallbacks();
                        try {
                            // Sleep a bit to save CPU
                            Thread.sleep(16);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                callbacks.start();
            }
        }
    }

    @SubscribeEvent
    void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        StartCore.core.close();
        StartCore.isEnabled = false;
    }

    @SubscribeEvent
    void onMsg(ClientChatReceivedEvent event) {
        String msg = event.message.getFormattedText();
        if(msg.startsWith("§f{\"server\":") && !checkedLoc){
            event.setCanceled(true);
            JsonParser parser= new JsonParser();
            JsonObject data = (JsonObject) parser.parse(event.message.getUnformattedText());
            if(data.get("server").toString().contains("lobby")){
                secondLine = "In a lobby";
            }else if(data.has("mode")){
                if(data.get("gametype").toString().equals("\"ARCADE\"")){
                    secondLine = "In a party";
                }else if(data.get("gametype").toString().equals("\"SKYBLOCK\"") && data.has("map")){
                    secondLine = data.get("map").toString().replaceAll("\"","").substring(0, 1).toUpperCase() + data.get("map").toString().replaceAll("\"","").substring(1).toLowerCase(Locale.ROOT);
                }else {
                    secondLine = Utils.getMode(data.get("mode").toString());
                }
            }else{
                secondLine = "In a party";
            }
            checkedLoc = true;
        }else if (msg.startsWith("§6Party Members (")) {
            String amount[] = msg.split("[()]");
            partyMembers = Integer.parseInt(amount[1]);
        } else if (msg.startsWith("§cThe party was disbanded because all invites expired and the party was empty")) {
            partyMembers = 1;
            partyLeader = true;
        } else if (msg.endsWith("§r§ehas disbanded the party!§r")) {
            partyMembers = 1;
            partyLeader = true;
        } else if (msg.endsWith("§r§ejoined the party.§r")) {
            partyMembers++;
        } else if (msg.endsWith("§r§ehas left the party.§r")) {
            partyMembers--;
        } else if (msg.startsWith("§eYou left the party.§r")) {
            partyMembers = 1;
            partyLeader = true;
        } else if (msg.startsWith("§eYou have joined") && msg.endsWith("§r§eparty!§r")) {
            partyMembers = 2;
            partyLeader = false;
        } else if (msg.contains("has promoted") && msg.contains("§r§eto Party Moderator§r") && msg.contains(Minecraft.getMinecraft().thePlayer.getName())) {
            partyLeader = true;
        } else if (msg.contains("has demoted") && msg.contains("§r§eto Party Member§r") && msg.contains(Minecraft.getMinecraft().thePlayer.getName())) {
            partyLeader = false;
        } else if (msg.startsWith("§eThe party was transferred to") && msg.contains(Minecraft.getMinecraft().thePlayer.getName())) {
            partyLeader = true;
        } else if (msg.startsWith("§eThe party was transferred to")) {
            partyLeader = false;
        } else if (msg.endsWith("§r§ewas removed from the party because they disconnected§r")) {
            partyMembers--;
        } else if (msg.startsWith("§eYou'll be partying with:")) {
            partyMembers = 3;
            for (int i = 0; i < msg.length(); i++) {
                if (msg.charAt(i) == ',') {
                    partyMembers++;
                }
            }
        } else if (msg.startsWith("§eYou have been kicked from the party by")) {
            partyMembers = 1;
            partyLeader = true;
        } else if (msg.endsWith("§r§ehas been removed from the party.§r")) {
            partyMembers--;
        } else if (msg.startsWith("§dDungeon Finder §r§f>") && msg.contains("§r§ejoined the dungeon group!") && msg.contains(Minecraft.getMinecraft().thePlayer.getName())) {
            partyLeader = false;
        }else if (msg.startsWith("§dDungeon Finder §r§f>") && msg.contains("§r§ejoined the dungeon group!")) {
            partyMembers++;
        }
    }

    void updateRPC(String arg) throws IOException {
        try (Activity activity = new Activity()) {
            activity.setDetails(arg);
            activity.setState(secondLine);

            activity.timestamps().setStart(time);

            // We are in a party with 10 out of 100 people.
            activity.party().size().setMaxSize(settings.maxPartySize);
            activity.party().size().setCurrentSize(partyMembers);

            activity.assets().setLargeImage(Utils.getDiscordPic(arg));

            // Setting a join secret and a party ID causes an "Ask to Join" button to appear
            if (partyLeader && settings.enableInvites) {
                activity.party().setID(Minecraft.getMinecraft().thePlayer.getName());
                activity.secrets().setJoinSecret(Minecraft.getMinecraft().thePlayer.getUniqueID().toString());
            }

            // Finally, update the current activity to our activity
            StartCore.core.activityManager().updateActivity(activity);
        }
    }
}


