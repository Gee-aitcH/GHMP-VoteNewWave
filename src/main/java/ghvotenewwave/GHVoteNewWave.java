package ghvotenewwave;

import io.anuke.arc.Core;
import io.anuke.arc.collection.ObjectSet;
import io.anuke.arc.util.*;
import io.anuke.mindustry.core.NetClient;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.plugin.Plugin;

import static io.anuke.mindustry.Vars.*;

public class GHVoteNewWave extends Plugin{

    private VoteSession voting = null;
    private long cooldown = -1;

    public void registerServerCommands(CommandHandler handler){
        handler.register("ghnw", "[on/off|y/n]", "Vote for Sending a new Wave.", args -> {
            if (args.length == 0)
                Log.info(!Core.settings.getBool("ghnwmode", true) ? "The Plugin is Not Turned On Yet. Do 'ghnw on' to turn on'." : voting == null ? "[orange]No Voting Session is being hosted right now. Use /ghnw y or /ghnw n to start a new voting session." : voting.status());
            else switch (args[0]) {
                case "on": case "off":
                    Core.settings.put("ghnwmode", args[0].equals("on"));
                    break;
                case "y": case "n":
                    if (!Core.settings.getBool("ghnwmode", true)) return;
                    if (voting == null) voting = new VoteSession();
                    if (System.currentTimeMillis() > cooldown) voting.vote(args[0].equals("y") ? 3 : -3);
                    break;
                case "help":
                    Log.info("GHVoteNewWave: \n" +
                            "'ghna on/off' to Turn On/Off the Plugin.\n" +
                            "'ghna y/n' to (Host&)Vote.\n" +
                            "Notes: 2 x 'y' = -1 x 'n'. 2 Votes on Yes is equivalent to 1 Vote on No.\n" +
                            "A Vote is Passed When There are Over 34%.");
                    break;
            }
        });
    }

    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("ghnw", "[y/n]", "Vote for Sending a new Wave.", (args, player) -> {
            if(args.length == 0)
                player.sendMessage("[orange]" + (!Core.settings.getBool("ghnwmode", true) ? "The Plugin is Not Turned On Yet. Do 'ghnw on' to turn on'." : voting == null ? "[orange]No Voting Session is being hosted right now. Use /ghnw y or /ghnw n to start a new voting session." : voting.status()));
            else switch (args[0]) {
                case "on": case "off":
                    if(player.isAdmin) Core.settings.put("ghnwmode", args[0].equals("on"));
                    break;
                case "y": case "n":
                    if (!Core.settings.getBool("ghnwmode", true)) return;
                    if (voting == null) voting = new VoteSession();
                    if (System.currentTimeMillis() <= cooldown)
                        player.sendMessage("Penalty is on right now, please wait for the 5 mins to pass.");
                    else if (voting.voted.contains(player.uuid) || voting.voted.contains(netServer.admins.getInfo(player.uuid).lastIP))
                        player.sendMessage("You have Voted already.");
                    else
                        voting.vote(player, args[0].equals("y"));
                    break;
                case "help":
                    player.sendMessage("GHVoteNewWave: \n" +
                            "'ghna on/off' to Turn On/Off the Plugin.\n" +
                            (player.isAdmin ? "'ghna y/n' to (Host&)Vote.\n" : "") +
                            "Notes: 2 x 'y' = -1 x 'n'. 2 Votes on Yes is equivalent to 1 Vote on No.\n" +
                            "A Vote is Passed When There are Over 34%.");
                    break;
            }
        });
    }

    class VoteSession{
        int y, n;
        ObjectSet<String> voted = new ObjectSet<>();
        Timer.Task task;
        long start;
        float duration = 60;

        VoteSession(){
            y = n = 0;
            this.task = Timer.schedule(() -> {
                if(!checkPass()){
                    Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes to send a new wave. Penalty: 5 mins vote cooldown."));
                    task.cancel();
                    cooldown = System.currentTimeMillis() + 5 * 60 * 1000;
                }
            }, duration);
            this.start = System.currentTimeMillis();
        }

        void vote(int amount){
            if(amount > 0) y += amount; else n += amount;
            Call.sendMessage(status());
            checkPass();
        }
        void vote(Player player, boolean agree){
            vote((player.isAdmin ? 2 : 1) * (agree ? 1 : -1));
            voted.addAll(player.uuid, netServer.admins.getInfo(player.uuid).lastIP);
            Call.sendMessage("[orange]"  + NetClient.colorizeName(player.id, player.name) + "[lightgray] has voted to" + (agree ? "" : " not to") + " send a new wave. \n" +
                    status() + "[lightgray]Type[orange] /vote <y/n>[] to agree/disagree.");
            checkPass();
        }

        boolean checkPass(){
            if(currentVotes() >= votesRequired()){
                Call.sendMessage("[green]Vote for Sending a New Wave is Passed. New Wave will be Spawned.");
                state.wavetime = 0f;
                task.cancel();
                cooldown = System.currentTimeMillis() + 1000;
                voting = null;
                return true;
            }
            return false;
        }

        float currentVotes(){
            return (y - n * 2f) / playerGroup.all().size;
        }

        float votesRequired(){
            return 0.34f;
        }

        String status(){
            return "[lightgray]Currently, you are at [orange]" + currentVotes() * 100 + "%[]. You need [orange]" + votesRequired() * 100 + "%[], " +
                    "or at least [orange]" + Math.ceil((votesRequired() - currentVotes()) * playerGroup.all().size) + "[] more votes. " +
                    "You still have [accent]" + (duration - (System.currentTimeMillis() - start) / 1000f) + "s[] to Vote.";
        }
    }
}
