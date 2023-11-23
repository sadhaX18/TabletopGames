package evaluation.tournaments;

import core.AbstractParameters;
import core.AbstractPlayer;
import evaluation.RunArg;
import evaluation.listeners.IGameListener;
import evaluation.optimisation.NTBEA;
import evaluation.optimisation.NTBEAParameters;
import games.GameType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import players.IAnyTimePlayer;
import players.PlayerFactory;
import utilities.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static evaluation.RunArg.*;
import static evaluation.tournaments.AbstractTournament.TournamentMode.ONE_VS_ALL;
import static utilities.Utils.getArg;

/**
 * This runs experiments to plot the skills ladder of a game.
 * Given a total budget of games, a starting budget, and an incremental factor, it will run a tournament
 * using one agent with a higher budget, and the rest with the lower budget. Equal numbers of games will be run with the
 * higher budget agent in different player positions.
 */
public class SkillLadder {

    public static void main(String[] args) {
        List<String> argsList = Arrays.asList(args);
        if (argsList.contains("--help") || argsList.contains("-h")) {
            RunArg.printHelp(Usage.SkillLadder);
            return;
        }

        // Config
        Map<RunArg, Object> config = parseConfig(args, RunArg.Usage.SkillLadder);

        String setupFile = config.getOrDefault(RunArg.config, "").toString();
        if (!setupFile.isEmpty()) {
            // Read from file instead
            try {
                FileReader reader = new FileReader(setupFile);
                JSONParser parser = new JSONParser();
                JSONObject json = (JSONObject) parser.parse(reader);
                config = parseConfig(json, RunArg.Usage.SkillLadder);
            } catch (FileNotFoundException ignored) {
                throw new AssertionError("Config file not found : " + setupFile);
                //    parseConfig(runGames, args);
            } catch (IOException | ParseException e) {
                throw new RuntimeException(e);
            }
        }
        if (config.get(RunArg.game).equals("all")) {
            System.out.println("No game provided. Please provide a game.");
            return;
        }
        GameType game = GameType.valueOf(config.get(RunArg.game).toString());
        if (game == GameType.GameTemplate) {
            System.out.println("No game provided. Please provide a game.");
            return;
        }
        int nPlayers = (int) config.get(RunArg.nPlayers);
        int minPlayers = nPlayers;
        int maxPlayers = nPlayers;
        if (nPlayers == -1) {
            String playerRange = config.get(RunArg.playerRange).toString();
            if (playerRange.isEmpty()) {
                System.out.println("No player range provided. Please provide a player range.");
                return;
            }
            if (playerRange.equalsIgnoreCase("all")) {
                minPlayers = game.getMinPlayers();
                maxPlayers = game.getMaxPlayers();
            } else {
                String[] split = playerRange.split("-");
                minPlayers = Integer.parseInt(split[0]);
                maxPlayers = Integer.parseInt(split[1]);
            }
        } else if (nPlayers < game.getMinPlayers() || nPlayers > game.getMaxPlayers()) {
            System.out.println("Invalid number of players for game " + game + ". Please provide a valid number of players.");
            return;
        }
        int NTBEABudget = (int) config.get(RunArg.NTBEABudget);
        String searchSpace = config.get(RunArg.searchSpace).toString();
        if (NTBEABudget > 0 && searchSpace.isEmpty()) {
            System.out.println("No search space file provided. Please provide a search space file.");
            return;
        }
        String player = config.get(RunArg.player).toString();
        if (player.isEmpty()) {
            System.out.println("Please specify a player");
            return;
        }

        int gamesPerIteration = (int) config.get(matchups);
        int startingTimeBudget = (int) config.get(startBudget);
        int iterations = (int) config.get(RunArg.iterations);
        int timeBudgetMultiplier = (int) config.get(RunArg.multiplier);
        String destDir = config.get(RunArg.destDir).toString();
        String gameParams = config.get(RunArg.gameParams).toString();
        @SuppressWarnings("unchecked") List<String> listenerClasses = (List<String>) config.get(listener);
        String startSettings = config.get(RunArg.startSettings).toString();
        boolean runAgainstAllAgents = (boolean) config.get(grid);
        int startGridBudget = (int) config.get(gridStart);
        int startMinorGridBudget = (int) config.get(gridMinorStart);

        for (int p = minPlayers; p <= maxPlayers; p++) {
            nPlayers = p;
            String destDirPlayer = destDir + File.separator + "Players_" + nPlayers;
            config.put(RunArg.nPlayers, p);

            AbstractParameters params = AbstractParameters.createFromFile(game, gameParams);
            int[] currentBestSettings = new int[0];
            List<AbstractPlayer> allAgents = new ArrayList<>(iterations);
            AbstractPlayer firstAgent;
            if (NTBEABudget > 0) {
                NTBEAParameters ntbeaParameters = constructNTBEAParameters(config, startingTimeBudget, NTBEABudget);
                ntbeaParameters.repeats = Math.max(nPlayers, ntbeaParameters.repeats);
                NTBEA ntbea = new NTBEA(ntbeaParameters, game, nPlayers);
                ntbeaParameters.printSearchSpaceDetails();
                if (startSettings.isEmpty()) {
                    // first we tune the minimum budget against the default starting agent
                    Pair<Object, int[]> results = ntbea.run();
                    firstAgent = (AbstractPlayer) results.a;
                    currentBestSettings = results.b;
                } else {
                    // or we use the specified starting settings
                    currentBestSettings = Arrays.stream(startSettings.split("")).mapToInt(Integer::parseInt).toArray();
                    firstAgent = (AbstractPlayer) ntbeaParameters.searchSpace.getAgent(currentBestSettings);
                }
            } else {
                // We are not tuning between rungs, and just update the budget in the player definition
                firstAgent = PlayerFactory.createPlayer(player, s -> s.replaceAll("-999", Integer.toString(startingTimeBudget)));
            }
            firstAgent.setName("Budget " + startingTimeBudget);
            allAgents.add(firstAgent);

            for (int i = 0; i < iterations; i++) {
                int newBudget = (int) (Math.pow(timeBudgetMultiplier, i + 1) * startingTimeBudget);
                if (NTBEABudget > 0) {
                    NTBEAParameters ntbeaParameters = constructNTBEAParameters(config, newBudget, NTBEABudget);
                    // ensure we have one repeat for each player position (to make the tournament easier)
                    // we will have one from the elite set, so we need nPlayers-1 more
                    ntbeaParameters.repeats = Math.max(nPlayers - 1, ntbeaParameters.repeats);
                    NTBEA ntbea = new NTBEA(ntbeaParameters, game, nPlayers);
                    AbstractPlayer benchmark = allAgents.get(i).copy();
                    if (benchmark instanceof IAnyTimePlayer) {
                        ((IAnyTimePlayer) benchmark).setBudget(newBudget);
                    }
                    ntbea.setOpponents(Collections.singletonList(benchmark));
                    ntbea.addElite(currentBestSettings);

                    Pair<Object, int[]> results = ntbea.run();
                    allAgents.add((AbstractPlayer) results.a);
                    currentBestSettings = results.b;
                } else {
                    allAgents.add(PlayerFactory.createPlayer(player, s -> s.replaceAll("-999", String.valueOf(newBudget))));
                }
                allAgents.get(i + 1).setName("Budget " + newBudget);
                if (newBudget < startGridBudget) // we fast forward to where we want to start the grid
                    continue;
                // for each iteration we run a round robin tournament; either against just the previous agent (with the previous budget), or
                // if we have grid set to true, then against all previous agents, one after the other
                int startAgent = runAgainstAllAgents ? 0 : i;
                for (int agentIndex = startAgent; agentIndex <= i; agentIndex++) {
                    int otherBudget = (int) (Math.pow(timeBudgetMultiplier, agentIndex) * startingTimeBudget);
                    if (newBudget == startGridBudget && otherBudget < startMinorGridBudget) // we fast forward to where we want to start the minor grid
                        continue;
                    List<AbstractPlayer> agents = Arrays.asList(allAgents.get(i + 1), allAgents.get(agentIndex));
                    Map<RunArg, Object> configs = new HashMap<>();
                    configs.put(matchups, gamesPerIteration);
                    configs.put(byTeam, false);
                    RoundRobinTournament RRT = new RoundRobinTournament(agents, game, nPlayers, params, ONE_VS_ALL, configs);
                    RRT.verbose = false;
                    for (String listenerClass : listenerClasses) {
                        if (listenerClass.isEmpty()) continue;
                        IGameListener gameTracker = IGameListener.createListener(listenerClass, null);
                        RRT.getListeners().add(gameTracker);
                        if (runAgainstAllAgents) {
                            String[] nestedDirectories = new String[]{destDirPlayer, "Budget_" + newBudget + " vs Budget_" + otherBudget};
                            gameTracker.setOutputDirectory(nestedDirectories);
                        } else {
                            String[] nestedDirectories = new String[]{destDirPlayer, "Budget_" + newBudget};
                            gameTracker.setOutputDirectory(nestedDirectories);
                        }
                    }

                    long startTime = System.currentTimeMillis();
                    RRT.setResultsFile((destDirPlayer.isEmpty() ? "" : destDirPlayer + File.separator) + "TournamentResults.txt");
                    RRT.run();
                    long endTime = System.currentTimeMillis();
                    System.out.printf("%d games in %3d minutes\tBudget %5d win rate: %.1f%% +/- %.1f%%, mean rank %.1f +/- %.1f\tvs Budget %5d win rate: %.1f%% +/- %.1f%%, mean rank %.1f +/- %.1f%n",
                            gamesPerIteration, (endTime - startTime) / 60000,
                            newBudget,
                            RRT.getWinRate(0) * 100, RRT.getWinStdErr(0) * 100 * 2,
                            RRT.getOrdinalRank(0), RRT.getOrdinalStdErr(0) * 2,
                            otherBudget,
                            RRT.getWinRate(1) * 100, RRT.getWinStdErr(1) * 100 * 2,
                            RRT.getOrdinalRank(1), RRT.getOrdinalStdErr(1) * 2
                    );
                }
            }
        }
    }

    private static NTBEAParameters constructNTBEAParameters(Map<RunArg, Object> config, int agentBudget, int gameBudget) {
        int NTBEARunsBetweenRungs = 4;
        double NTBEABudgetOnTournament = 0.50; // the complement will be spent on NTBEA runs

        NTBEAParameters ntbeaParameters = new NTBEAParameters(config, s -> s.replaceAll("-999", Integer.toString(agentBudget)));

        ntbeaParameters.destDir = ntbeaParameters.destDir + File.separator + "Players_" + config.get(nPlayers) + File.separator + "Budget_" + agentBudget + File.separator + "NTBEA";
        ntbeaParameters.repeats = NTBEARunsBetweenRungs;

        ntbeaParameters.tournamentGames = (int) (gameBudget * NTBEABudgetOnTournament);
        ntbeaParameters.iterationsPerRun = (gameBudget - ntbeaParameters.tournamentGames) / NTBEARunsBetweenRungs;
        ntbeaParameters.evalGames = 0;
        ntbeaParameters.opponentDescriptor = config.get(player).toString();
        ntbeaParameters.logFile = "NTBEA_Runs.log";
        return ntbeaParameters;
    }

}
