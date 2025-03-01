package games.monopolydeal.gui;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.Game;
import core.components.Deck;
import games.monopolydeal.MonopolyDealGameState;
import games.monopolydeal.MonopolyDealParameters;
import games.monopolydeal.cards.MonopolyDealCard;
import gui.AbstractGUIManager;
import gui.GamePanel;
import gui.IScreenHighlight;
import players.human.ActionController;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Set;

import static core.CoreConstants.VisibilityMode.VISIBLE_TO_ALL;

/**
 * <p>This class allows the visualisation of the game. The game components (accessible through {@link Game#getGameState()}
 * should be added into {@link javax.swing.JComponent} subclasses (e.g. {@link javax.swing.JLabel},
 * {@link javax.swing.JPanel}, {@link javax.swing.JScrollPane}; or custom subclasses such as those in {@link gui} package).
 * These JComponents should then be added to the <code>`parent`</code> object received in the class constructor.</p>
 *
 * <p>An appropriate layout should be set for the parent GamePanel as well, e.g. {@link javax.swing.BoxLayout} or
 * {@link java.awt.BorderLayout} or {@link java.awt.GridBagLayout}.</p>
 *
 * <p>Check the super class for methods that can be overwritten for a more custom look, or
 * {@link games.terraformingmars.gui.TMGUI} for an advanced game visualisation example.</p>
 *
 * <p>A simple implementation example can be found in {@link games.tictactoe.gui.TicTacToeGUIManager}.</p>
 */
public class MonopolyDealGUIManager extends AbstractGUIManager {

    // Display Settings
    final static int playerAreaWidth = 300;
    final static int playerAreaHeight = 130;
    final static int MonopolyDealCardWidth = 107;
    final static int MonopolyDealCardHeight = 150;

    final static int HandBankWidth = 53;
    final static int HandBankHeight = 75;
    final static int PropertyWidth = 36;
    final static int PropertyHeight = 50;

    MonopolyDealDeckView discardPile;
    MonopolyDealDeckView drawPile;
    int activePlayer = -1;
    MonopolyDealDeckView[] playerBanks;
    MonopolyDealCardsView[] playerHands;
    MonopolyDealCardsView[][] playerProperties;

    Border highlightActive = BorderFactory.createLineBorder(new Color(47, 132, 220), 3);
    Border[] playerViewBorders;
    public MonopolyDealGUIManager(GamePanel parent, Game game, ActionController ac, Set<Integer> humanID) {
        super(parent, game, ac, humanID);
        AbstractGameState gameState = game.getGameState();
        if (game != null) {
            // Initialise active player
            activePlayer = gameState.getCurrentPlayer();

            // Find required size of window
            int nPlayers = gameState.getNPlayers();
            int nHorizAreas = 1 + (nPlayers <= 3 ? 2 : nPlayers == 4 ? 3 : nPlayers <= 8 ? 4 : 5);
            double nVertAreas = 3.5;
            this.width = playerAreaWidth * nHorizAreas;
            this.height = (int) (playerAreaHeight * nVertAreas);

            MonopolyDealGameState mdgs = (MonopolyDealGameState) gameState;
            MonopolyDealParameters mdp = (MonopolyDealParameters) gameState.getGameParameters();

            playerHands = new MonopolyDealCardsView[nPlayers];
            playerBanks = new MonopolyDealDeckView[nPlayers];
            playerProperties = new MonopolyDealCardsView[nPlayers][10];
            playerViewBorders = new Border[nPlayers];
            JPanel mainGameArea = new JPanel();
            mainGameArea.setLayout(new BorderLayout());

            // Player hands go on the edges
            String[] locations = new String[]{BorderLayout.NORTH, BorderLayout.EAST, BorderLayout.SOUTH, BorderLayout.WEST};
            JPanel[] sides = new JPanel[]{new JPanel(), new JPanel(), new JPanel(), new JPanel()};
            int next = 0;

            for (int i = 0; i < nPlayers; i++){
                JPanel playerView = new JPanel();
                playerView.setLayout(new BorderLayout());
                MonopolyDealCardsView playerHand = new MonopolyDealCardsView(mdgs.getPlayerHand(i),i,humanID,mdp.getDataPath(), MonopolyDealGUIType.Hand);
                MonopolyDealDeckView playerBank = new MonopolyDealDeckView(-1, mdgs.getDrawPile(), true, mdp.getDataPath(), new Rectangle(0, 0, MonopolyDealCardWidth/2, MonopolyDealCardHeight/2), MonopolyDealCardWidth/2, MonopolyDealCardHeight/2);
                playerView.add(playerBank,BorderLayout.EAST);
                // Adding player properties
                JPanel propertiesNorthView = new JPanel();
                JPanel propertiesSouthView = new JPanel();
                for(int j=0;j<10;j++){
                    MonopolyDealCardsView playerProperty = new MonopolyDealCardsView(new Deck<MonopolyDealCard>("Property of Player"+i,VISIBLE_TO_ALL),i,humanID,mdp.getDataPath(), MonopolyDealGUIType.Property);
                    playerProperties[i][j] = playerProperty;
                    if(j<5) propertiesNorthView.add(playerProperty);
                    else propertiesSouthView.add(playerProperty);
                }
                JPanel propertiesView = new JPanel();
                propertiesView.setLayout(new BorderLayout());
                propertiesView.add(propertiesNorthView,BorderLayout.NORTH);
                propertiesView.add(propertiesSouthView,BorderLayout.SOUTH);

                playerView.add(propertiesView,BorderLayout.NORTH);
                playerView.add(playerHand,BorderLayout.SOUTH);

                // Get agent name
                String[] split = game.getPlayers().get(i).getClass().toString().split("\\.");
                String agentName = split[split.length - 1];

                // Create border, layouts and keep track of this view
                TitledBorder title = BorderFactory.createTitledBorder(
                        BorderFactory.createEtchedBorder(EtchedBorder.LOWERED), "Player " + i + " [" + agentName + "]",
                        TitledBorder.CENTER, TitledBorder.ABOVE_TOP);
                playerViewBorders[i] = title;
                playerHand.setBorder(title);

                sides[next].add(playerView);
                sides[next].setLayout(new GridBagLayout());
                next = (next + 1) % (locations.length);
                playerHands[i] = playerHand;
                playerBanks[i] = playerBank;
            }
            for (int i = 0; i < locations.length; i++) {
                mainGameArea.add(sides[i], locations[i]);
            }

            // Discard and draw piles go in the center
            JPanel centerArea = new JPanel();
            centerArea.setLayout(new BoxLayout(centerArea, BoxLayout.X_AXIS));
            discardPile = new MonopolyDealDeckView(-1, mdgs.getDiscardPile(), true, mdp.getDataPath(), new Rectangle(0, 0, MonopolyDealCardWidth, MonopolyDealCardHeight), MonopolyDealCardWidth, MonopolyDealCardHeight);
            drawPile = new MonopolyDealDeckView(-1, mdgs.getDrawPile(), gameState.getCoreGameParameters().alwaysDisplayFullObservable, mdp.getDataPath(), new Rectangle(0, 0, MonopolyDealCardWidth, MonopolyDealCardHeight), MonopolyDealCardWidth, MonopolyDealCardHeight);
            centerArea.add(drawPile);
            centerArea.add(discardPile);
            JPanel jp = new JPanel();
            jp.setLayout(new GridBagLayout());
            jp.add(centerArea);
            mainGameArea.add(jp, BorderLayout.CENTER);

            // Top area will show state information
            JPanel infoPanel = createGameStateInfoPanel("MonopolyDeal", gameState, width, defaultInfoPanelHeight);
            // Bottom area will show actions available
            JComponent actionPanel = createActionPanel(new IScreenHighlight[0], width, defaultActionPanelHeight, false, true, null, null, null);

            // Add all views to frame
            parent.setLayout(new BorderLayout());
            parent.add(mainGameArea, BorderLayout.CENTER);
            parent.add(infoPanel, BorderLayout.NORTH);
            parent.add(actionPanel, BorderLayout.SOUTH);
            parent.revalidate();
            parent.setVisible(true);
            parent.repaint();
        }
        // TODO: set up GUI components and add to `parent`
    }

    /**
     * Defines how many action button objects will be created and cached for usage if needed. Less is better, but
     * should not be smaller than the number of actions available to players in any game state.
     *
     * @return maximum size of the action space (maximum actions available to a player for any decision point in the game)
     */
    @Override
    public int getMaxActionSpace() {
        // TODO
        return 10;
    }

    /**
     * Updates all GUI elements given current game state and player that is currently acting.
     *
     * @param player    - current player acting.
     * @param gameState - current game state to be used in updating visuals.
     */
    @Override
    protected void _update(AbstractPlayer player, AbstractGameState gameState) {
        // TODO
        if (gameState != null){
            if (gameState.getCurrentPlayer() != activePlayer) {
                playerHands[activePlayer].playerHandView.setCardHighlight(-1);
                activePlayer = gameState.getCurrentPlayer();
            }
        }
        // Clearing all properties
        for(int i=0;i<gameState.getNPlayers();i++){
            for(int j=0;j<10;j++){
                playerProperties[i][j].clear();
            }
        }
        // Update decks and visibility
        MonopolyDealGameState mdgs = (MonopolyDealGameState) gameState;

        for (int i = 0; i < gameState.getNPlayers(); i++){
            playerHands[i].update(mdgs);
            playerHands[i].playerHandView.setFront(false);
            playerBanks[i].updateComponent(mdgs.getPlayerBank(i));
            for(int j=0;j < mdgs.getPropertySets(i).size(); j++){
                playerProperties[i][j].updateProperty(mdgs,i,j);
            }
            if (i == gameState.getCurrentPlayer() && (gameState.getCoreGameParameters().alwaysDisplayCurrentPlayer
                    || humanPlayerId.contains(i)
                    || gameState.getCoreGameParameters().alwaysDisplayFullObservable)) {
                playerHands[i].playerHandView.setFront(true);
                playerHands[i].setFocusable(true);
            }

            // Highlight active player
            if (i == gameState.getCurrentPlayer()) {
                Border compound = BorderFactory.createCompoundBorder(
                        highlightActive, playerViewBorders[i]);
                playerHands[i].setBorder(compound);
            } else {
                playerHands[i].setBorder(playerViewBorders[i]);
            }
        }

        discardPile.updateComponent(mdgs.getDiscardPile());
        drawPile.setFocusable(true);
        drawPile.updateComponent(mdgs.getDrawPile());
        if (gameState.getCoreGameParameters().alwaysDisplayFullObservable) {
            drawPile.setFront(true);
        }
    }
}