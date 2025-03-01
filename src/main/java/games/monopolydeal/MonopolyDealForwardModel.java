package games.monopolydeal;

import core.AbstractGameState;
import core.StandardForwardModel;
import core.actions.AbstractAction;
import core.components.Deck;
import games.monopolydeal.actions.*;
import games.monopolydeal.cards.CardType;
import games.monopolydeal.cards.MonopolyDealCard;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * <p>The forward model contains all the game rules and logic. It is mainly responsible for declaring rules for:</p>
 * <ol>
 *     <li>Game setup</li>
 *     <li>Actions available to players in a given game state</li>
 *     <li>Game events or rules applied after a player's action</li>
 *     <li>Game end</li>
 * </ol>
 */
public class MonopolyDealForwardModel extends StandardForwardModel {

    /**
     * Initializes all variables in the given game state. Performs initial game setup according to game rules, e.g.:
     * <ul>
     *     <li>Sets up decks of cards and shuffles them</li>
     *     <li>Gives player cards</li>
     *     <li>Places tokens on boards</li>
     *     <li>...</li>
     * </ul>
     *
     * @param firstState - the state to be modified to the initial game state.
     */
    @Override
    protected void _setup(AbstractGameState firstState) {
        // initialization of variables and game setup
        MonopolyDealGameState state = (MonopolyDealGameState) firstState;
        state._reset();
        state.deckEmpty = false;
        MonopolyDealParameters params = state.params;
        state.actionsLeft = params.ACTIONS_PER_TURN;
        state.boardModificationsLeft = params.BOARD_MODIFICATIONS_PER_TURN;

        // Add cards to Deck
        for (CardType cT:params.cardsIncludedInGame.keySet()) {
            for(int i =0;i<params.cardsIncludedInGame.get(cT);i++){
                state.drawPile.add(MonopolyDealCard.create(cT));
            }
        }
        //Shuffle Deck
        state.drawPile.shuffle(state.rnd);
        //Deal 5 cards to each player
        for(int i=0;i< state.getNPlayers();i++) {
            state.drawCard(i,params.INITIAL_DEAL);
        }
        state.setGamePhase(MonopolyDealGameState.MonopolyDealGamePhase.Play);
        // Draw cards at the start of the turn
        state.drawCard(state.getFirstPlayer(),params.DRAWS_PER_TURN);
    }

    /**
     * Calculates the list of currently available actions, possibly depending on the game phase.
     * @return - List of AbstractAction objects.
     */
    @Override
    protected List<AbstractAction> _computeAvailableActions(AbstractGameState gameState) {
        MonopolyDealGameState state = (MonopolyDealGameState) gameState;
        int playerID = state.getCurrentPlayer();

        switch (state.getGamePhase().toString()){
            case "Play":
                if (state.actionsLeft > 0){
                    List<AbstractAction> availableActions = new ArrayList<>();
                    if(state.checkForActionCards(playerID))
                        availableActions.add(new PlayActionCard(playerID));

                    availableActions.add(new AddToBoard(playerID));

                    if(state.canModifyBoard(playerID))
                        availableActions.add(new ModifyBoard(playerID));

                    availableActions.add(new EndPhase());
                    return availableActions;
                }
                return Collections.singletonList(new EndPhase());
            case "Discard":
                if(state.playerHands[playerID].stream().count()>state.params.HAND_SIZE){
                    List<AbstractAction> availableActions = new ArrayList<>();
                    Deck<MonopolyDealCard> playerHand = state.playerHands[playerID];
                    for (int i=0;i<playerHand.getSize();i++) {
                        if(!availableActions.contains(new DiscardCard(playerHand.get(i).cardType(),playerID)))
                            availableActions.add(new DiscardCard(playerHand.get(i).cardType(),playerID));
                    }
                    return availableActions;
                }
            default:
                throw new AssertionError("Unknown Game Phase " + state.getGamePhase());
        }
    }

    // Draw cards at start of turn
//    @Override
//    protected void _beforeAction(AbstractGameState currentState, AbstractAction actionChosen) {
//        MonopolyDealGameState state = (MonopolyDealGameState) currentState;
//        if(state.turnStart){
//            int currentPlayer = state.getCurrentPlayer();
//            if(state.playerHands[currentPlayer].getSize() == 0){
//                state.drawCard(currentPlayer,state.params.DRAWS_WHEN_EMPTY);
//            }
//            else{
//                state.drawCard(currentPlayer,state.params.DRAWS_PER_TURN);
//            }
//            state.turnStart = false;
//        }
//    }
    @Override
    protected void _afterAction(AbstractGameState currentState, AbstractAction actionTaken) {
        MonopolyDealGameState state = (MonopolyDealGameState) currentState;
        int playerID = state.getCurrentPlayer();
        if(state.checkForGameEnd())
            endGame(currentState);
        else {
            switch (state.getGamePhase().toString()) {
                case "Play":
                    if ((state.actionsLeft < 1 || actionTaken instanceof EndPhase) && !state.isActionInProgress()) {
                        if (state.playerHands[playerID].getSize() > state.params.HAND_SIZE) {
                            state.setGamePhase(MonopolyDealGameState.MonopolyDealGamePhase.Discard);
                        } else {
                            if (state.getCurrentPlayer() == state.getNPlayers() - 1) endRound(state);
                            else endPlayerTurn(currentState);
                            state.endTurn();
                        }
                    }
                    break;
                case "Discard":
                    if (state.playerHands[playerID].getSize() <= state.params.HAND_SIZE) {
                        state.setGamePhase(MonopolyDealGameState.MonopolyDealGamePhase.Play);
                        if (state.getCurrentPlayer() == state.getNPlayers() - 1) endRound(state);
                        else endPlayerTurn(currentState);
                        state.endTurn();
                    }
                    break;
                default:
                    throw new AssertionError("Unknown Game Phase " + state.getGamePhase());
            }
        }
    }
}
