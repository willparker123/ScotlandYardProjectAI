package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import java.util.Objects;
import static uk.ac.bris.cs.scotlandyard.model.Colour.*;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Node;
import java.lang.Iterable;
import java.util.Iterator;
import uk.ac.bris.cs.gamekit.graph.Graph;
import com.google.common.collect.ImmutableMap;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import java.util.Map;
import com.google.common.collect.ImmutableSet;
import java.util.NoSuchElementException;



public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {

    //initialising variables for model
    public List<Boolean> rounds;
    public ImmutableGraph<Integer, Transport> graph;
    private List<Spectator> spectators = new ArrayList<>();
    public ArrayList<PlayerConfiguration> playerConfigurations = new ArrayList<>();
    public ArrayList<ScotlandYardPlayer> players;
    private Set<Colour> setWinners = new HashSet<Colour>();
    //index of the playerConfigurations/players array which is the current player
    public int currentPlayerIndex = 0;
    //index of the current round number
    public int currentRoundIndex = 0;
    //true if move is in the process of being made
    public boolean waitingForCallback = false;
    //true if doublemove is being played
    private boolean dbMove = false;
    private boolean dbMoveRound = false;
    //mrX's last revealed location
    public int mrXLastLocation = 0;



    //initialising variables for duplicate checksum
    private Set<Integer> setLocations = new HashSet<>();
    private Set<Colour> setColours = new HashSet<>();



    public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
                             PlayerConfiguration mrX, PlayerConfiguration firstDetective,
                             PlayerConfiguration... restOfTheDetectives) {

        currentRoundIndex = 0;
        //empty/null checksums for model variables
        if (rounds.isEmpty()) {
            throw new IllegalArgumentException("Empty Rounds");
        }
        if (graph.isEmpty()) {
            throw new IllegalArgumentException("Empty Graph");
        }
        if (mrX.colour.isDetective() || mrX.colour!=BLACK) {
            throw new IllegalArgumentException("MrX is not Black");
        }
        else {
            this.rounds = Objects.requireNonNull(rounds);
            this.playerConfigurations.add(0,requireNonNull(mrX));
            this.playerConfigurations.add(1,requireNonNull(firstDetective));
            this.graph = new ImmutableGraph <Integer, Transport>(graph);

            for (PlayerConfiguration detective : restOfTheDetectives) {
                this.playerConfigurations.add(requireNonNull(detective));
            }

            spectators.forEach(Objects::requireNonNull);
        }
        checkDuplicates();
        checkTickets();
        this.players = makeListPlayers(playerConfigurations);
        isGameOver(true);
        if (isGameOver(true)) update(this, getWinningPlayers());
    }

    //checks for duplicate locations and colours in the "playerConfigurations" set
    public void checkDuplicates() {
        for (PlayerConfiguration player : playerConfigurations) {
            if (setLocations.contains(player.location)) throw new IllegalArgumentException("Duplicate Player Location");
            setLocations.add(player.location);
            if (setColours.contains(player.colour)) throw new IllegalArgumentException("Duplicate Player Colour");
            setColours.add(player.colour);
        }
    }

    //checks for invalid tickets and mappings
    public void checkTickets() {
        for (PlayerConfiguration player : playerConfigurations) {
            //all keys in the 'tickets' Map
            Set<Ticket> tickets = player.tickets.keySet();
            Map<Ticket, Integer> ptickets = player.tickets;

            if (tickets.size()!=5) {
                throw new IllegalArgumentException("Map has missing tickets");
            }
            if (ptickets.size()!=5) {
                throw new IllegalArgumentException("Player has missing tickets");
            }
            if (player.colour!=BLACK && (ptickets.get(DOUBLE)!=0 || ptickets.get(SECRET)!=0)) {
                throw new IllegalArgumentException("Player has invalid tickets");
            }
        }
    }

    //ITERATOR: method which turns the player configurations into a list of ScotlandYardPlayer objects
    public ArrayList<ScotlandYardPlayer> makeListPlayers(ArrayList<PlayerConfiguration> playerconfigs) {
        ArrayList<ScotlandYardPlayer> list = new ArrayList<>();
        Iterator<PlayerConfiguration> iterator = playerconfigs.iterator();
        while (iterator.hasNext()) {
            PlayerConfiguration x = iterator.next();
            ScotlandYardPlayer player = new ScotlandYardPlayer(x.player, x.colour, x.location, x.tickets);
            list.add(requireNonNull(player));
        }
        return list;
    }

    //OBSERVER: spectators are observers, this is observable. has checksums for duplicates/null
    @Override
    public void registerSpectator(Spectator spectator) {
        Objects.requireNonNull(spectator);
        if (!spectators.contains(spectator)) {
            spectators.add(requireNonNull(spectator));
        }
        else throw new IllegalArgumentException("Invalid Spectator");
    }

    //OBSERVER: spectators are observers, this is observable. has checksums for duplicates/null
    @Override
    public void unregisterSpectator(Spectator spectator) {
        Objects.requireNonNull(spectator);
        if (spectators.contains(spectator)) {
            spectators.remove(requireNonNull(spectator));
        }
        else throw new IllegalArgumentException("Invalid Spectator");
    }

    //OVERLOADING: updates all of the spectators with a notify function chosen based on the arguments supplied
    public void update(ScotlandYardView view, Move move) {
        List<Spectator> spectators = new ArrayList<>(getSpectators());
        for (Spectator s : spectators) {
            s.onMoveMade(view, move);
        }
    }
    public void update(ScotlandYardView view, int round) {
        List<Spectator> spectators = new ArrayList<>(getSpectators());
        for (Spectator s : spectators) {
            s.onRoundStarted(view, round);
        }
    }
    public void update(ScotlandYardView view) {
        List<Spectator> spectators = new ArrayList<>(getSpectators());
        for (Spectator s : spectators) {
            s.onRotationComplete(view);
        }
    }
    public void update(ScotlandYardView view, Set<Colour> winningPlayers) {
        List<Spectator> spectators = new ArrayList<>(getSpectators());
        for (Spectator s : spectators) {
            s.onGameOver(view, winningPlayers);
        }
    }

    //returns true if the destination with value 'i' has a player on it
    public boolean destinationHasPlayer(int i) {
        for (ScotlandYardPlayer p : players) {
            if (p.location()==i && p.colour().isDetective()) return true;
        }
        return false;
    }

    //POLYMORPHISM: returns true if the player has at least 1 ticket of the given ticket type
    public boolean playerHasTicketsAvailable(Colour colour, Ticket ticket) {
        int i;
        try {
            i = getPlayerTickets(colour, ticket).get();
        } catch (NoSuchElementException e) {
            i = 0;
        } if (i>=1) return true;
        else return false;
    }
    public boolean playerHasTicketsAvailable(Colour colour, Ticket ticket, int n) {
        int i;
        try {
            i = getPlayerTickets(colour, ticket).get();
        } catch (NoSuchElementException e) {
            i = 0;
        } if (i>=n) return true;
        else return false;
    }

    //returns true if the move is in the set of valid moves (made by getValidMoves)
    public boolean isValidMove(Move move) {
        if (getValidMoves(players.get(currentPlayerIndex)).contains(move)) {
            return true;
        }
        else return false;
    }

    public int roundRemaining() {
        return getRounds().size() - getCurrentRound();
    }

    //GENERICS: returns a set of valid moves for a given player, uses Collection, Set, Iterator; contains all
    //           valid move logic
    public Set<Move> getValidMoves(ScotlandYardPlayer player) {
        Set<Move> cplayerMoves = new HashSet<>();
        Colour colour = player.colour();
        Integer location = player.location();

        Node<Integer> nodeL = graph.getNode(location);
        Collection<Edge<Integer, Transport>> e;

        if (nodeL == null) {
            e = graph.getEdges();
        } else {
            e = graph.getEdgesFrom(Objects.requireNonNull(nodeL));
        }

        for (Edge<Integer, Transport> edge : e) {
            Integer destination = edge.destination().value();
            Ticket ticket = Ticket.fromTransport(edge.data());
            //if the reachable nodes don't have a player on them and the player has available tickets,
            // add this node as a possible TicketMove
            if (!destinationHasPlayer(destination)) {
                if (playerHasTicketsAvailable(colour, ticket)) {
                    cplayerMoves.add(new TicketMove(colour, ticket, destination));
                }
                if (playerHasTicketsAvailable(colour, SECRET)) {
                    cplayerMoves.add(new TicketMove(colour, SECRET, destination));
                }
                if (playerHasTicketsAvailable(colour, DOUBLE) && roundRemaining() >= 2) {
                    Node<Integer> nodeR = graph.getNode(destination);
                    Collection<Edge<Integer, Transport>> d;
                    if (nodeR == null) {
                        d = graph.getEdges();
                    } else {
                        d = graph.getEdgesFrom(Objects.requireNonNull(nodeR));
                    }
                    for (Edge<Integer, Transport> edge1 : d) {
                        Integer destination1 = edge1.destination().value();
                        Ticket ticket1 = Ticket.fromTransport(edge1.data());
                        boolean tickets = (ticket==ticket1 && playerHasTicketsAvailable(colour,ticket,2))
                                || (ticket != ticket1 && playerHasTicketsAvailable(colour,ticket1));
                        TicketMove firstMove = new TicketMove(colour, ticket, destination);
                        TicketMove secondMove = new TicketMove(colour, ticket1, destination1);

                        if ((destination1 == location || !destinationHasPlayer(destination1)) && tickets) {
                            cplayerMoves.add(new DoubleMove(colour, firstMove, secondMove));
                        } if ((destination1 == location || !destinationHasPlayer(destination1))
                                && playerHasTicketsAvailable(colour,SECRET)) {
                            TicketMove secretFirstMove = new TicketMove(colour,SECRET,destination);
                            TicketMove secretSecondMove = new TicketMove(colour,SECRET,destination1);
                            if (playerHasTicketsAvailable(colour,SECRET,2)) {
                                cplayerMoves.add(new DoubleMove(colour,secretFirstMove,secretSecondMove));
                            } if (playerHasTicketsAvailable(colour,ticket)) {
                                cplayerMoves.add(new DoubleMove(colour,firstMove,secretSecondMove));
                            } if (playerHasTicketsAvailable(colour,ticket1)) {
                                cplayerMoves.add(new DoubleMove(colour,secretFirstMove,secondMove));
                            }
                        }
                    }
                }
            }
        } if (colour.isDetective() && cplayerMoves.isEmpty()) {
            cplayerMoves.add(new PassMove(colour));
        }
        return cplayerMoves;
    }

    //OVERLOADING: overloaded visitor function which allows this to visit moves and perform specific logic/operations
    @Override
    public void visit(PassMove move) {
        if (getCurrentPlayer()==BLACK) {
            currentRoundIndex++;
            update(this, getCurrentRound());
        }
        if (currentPlayerIndex+1<players.size()) {
            currentPlayerIndex++;
        } else currentPlayerIndex = 0;
        update(this, move);
    }
    @Override
    public void visit(DoubleMove move) {
        ScotlandYardPlayer cplayer = getPlayerFromColour(move.colour()).get();
        if (rounds.get(getCurrentRound())) {
            if (rounds.get(getCurrentRound()+1)) {
                //true, true
                currentPlayerIndex++;
                update(this, move);
            } else {
                //true, false
                DoubleMove tm = new DoubleMove(move.colour(), move.firstMove().ticket(),
                        move.firstMove().destination(), move.secondMove().ticket(), move.firstMove().destination());
                currentPlayerIndex++;
                update(this, tm);
            }
        } else {
            if (rounds.get(getCurrentRound()+1)) {
                //false, true
                DoubleMove tm = new DoubleMove(move.colour(), move.firstMove().ticket(),
                        mrXLastLocation, move.secondMove().ticket(), move.secondMove().destination());
                currentPlayerIndex++;
                update(this, tm);
            } else {
                //false, false
                DoubleMove tm = new DoubleMove(move.colour(), move.firstMove().ticket(),
                        mrXLastLocation, move.secondMove().ticket(), mrXLastLocation);
                currentPlayerIndex++;
                update(this, tm);
            }
        }
        update(this, getCurrentRound());
        currentRoundIndex++;
        cplayer.removeTicket(DOUBLE);
        dbMove = false;
        dbMoveRound = true;
        this.visit(move.firstMove());
        dbMove = true;
        this.visit(move.secondMove());
        dbMove = false;
        dbMoveRound = false;
    }
    @Override
    public void visit(TicketMove move) {
        ScotlandYardPlayer cplayer = getPlayerFromColour(move.colour()).get();
        cplayer.removeTicket(move.ticket());
        cplayer.location(move.destination());
        if (cplayer.colour().isDetective()) {
            players.get(0).addTicket(move.ticket());
            if (currentPlayerIndex+1<players.size()) {
                if (!dbMove) currentPlayerIndex++;
            } else currentPlayerIndex = 0;
            update(this, move);
        } else if (cplayer.colour().isMrX()) {
            if (dbMoveRound) {
                if (rounds.get(getCurrentRound()-1)) {
                    mrXLastLocation = cplayer.location();
                }
            } else {
                if (rounds.get(getCurrentRound())) {
                    mrXLastLocation = cplayer.location();
                }
            } if (currentPlayerIndex+1<players.size()) {
                if (!dbMoveRound) currentPlayerIndex++;
            } else {
                if (!dbMoveRound) currentPlayerIndex = 0;
            }
            TicketMove tm = new TicketMove(move.colour(), move.ticket(), mrXLastLocation);
            update(this, tm);
            if (!dbMove) {
                update(this, getCurrentRound());
                currentRoundIndex++;
            }
        }
    }

    //starts the rotation through the players; resets the currentPlayerIndex, provides event handling and tries to
    //make a move from the given list of valid moves
    @Override
    public void startRotate() {
            if (!isGameOver()) {
                waitingForCallback = false;
                currentPlayerIndex = 0;
                for (ScotlandYardPlayer p : players) {
                    if (!waitingForCallback) {
                        waitingForCallback = true;
                        ScotlandYardPlayer cplayer = players.get(currentPlayerIndex);
                        Set<Move> cplayerMoves = getValidMoves(cplayer);
                        cplayer.player().makeMove(this, cplayer.location(), Objects.requireNonNull(cplayerMoves), this);
                    }
                }
                if (isGameOver() && !waitingForCallback) update(this, getWinningPlayers());
                else if (!waitingForCallback) update(this);
            } else throw new IllegalStateException("Cannot start rotation if game is already over.");
        }

    //CONSUMER: ScotlandYardModel is the consumer and this accept() method checks for null and illegal args
    //			also, finishes the waitingForCallback request
    @Override
    public void accept(Move move) {
        if (move==null) {
            throw new NullPointerException("Player tried to make a null move");
        }
        else if (!isValidMove(move)) {
            throw new IllegalArgumentException("Player tried to make an invalid move");
        }
        else {
            move.visit(this);
            waitingForCallback = false;
            dbMove = false;
        }
    }

    @Override
    public Collection<Spectator> getSpectators() {
        return Collections.unmodifiableList(spectators);
    }

    //ITERATOR: Iterates through the list of ScotlandYardPlayers and creates a list of Colours in the same order
    @Override
    public List<Colour> getPlayers() {
        ArrayList<Colour> playerColours = new ArrayList<>();
        Iterator<ScotlandYardPlayer> iterator = players.iterator();
        while (iterator.hasNext()) {
            ScotlandYardPlayer x = iterator.next();
            Colour playerColour = x.colour();
            playerColours.add(requireNonNull(playerColour));
        }
        return Collections.unmodifiableList(playerColours);
    }

    //TO FINISH
    @Override
    public Set<Colour> getWinningPlayers() {
        return ImmutableSet.copyOf(setWinners);
    }

    //ITERATOR: iterates through 'players' to find a match to the argument Colour
    @Override
    public Optional<Integer> getPlayerLocation(Colour colour) {
        Iterator<ScotlandYardPlayer> iterator = players.iterator();
        while (iterator.hasNext()) {
            ScotlandYardPlayer x = iterator.next();
            Colour playerColour = x.colour();
            if (playerColour == colour) {
                if (playerColour == BLACK) {
                    return Optional.of(mrXLastLocation);
                } else {
                    Optional<Integer> optionalInt = Optional.of(x.location());
                    return optionalInt;
                }
            }
        }
        return Optional.empty();
    }

    //OPTIONAL: returns how many of a certain ticket the given player (colour) has
    @Override
    public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
        Iterator<ScotlandYardPlayer> iterator = players.iterator();
        while (iterator.hasNext()) {
            ScotlandYardPlayer x = iterator.next();
            Colour playerColour = x.colour();
            if (playerColour == colour) {
                try {
                    Optional<Integer> optionalInt = Objects.requireNonNull(Optional.of(x.tickets().get(ticket)));
                    return optionalInt;
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    //returns true if the game is over
    @Override
    public boolean isGameOver() {
        //if mrX is cornered
        boolean allDestinationsHavePlayers = false;
        for (Move m : getValidMoves(players.get(0))) {
            if ((m instanceof TicketMove) && destinationHasPlayer(((TicketMove) m).destination())) {
                allDestinationsHavePlayers = true;
            } else allDestinationsHavePlayers = false;
        }
        //if all detectives have no moves
        int i = 0;
        for (Colour c : getPlayers()) {
            ScotlandYardPlayer p = getPlayerFromColour(c).get();
            if (p.colour().isDetective() && (p.hasTickets(BUS) || p.hasTickets(TAXI) || p.hasTickets(UNDERGROUND))) {
                i++;
            }
        } if (i==0) {
            setWinners.add(BLACK);
            getWinningPlayers();
            return true;
        }
        if (getValidMoves(players.get(0)).isEmpty() && getCurrentPlayer().isMrX()) { //if mrX can't move
            setWinners.addAll(getPlayers());
            setWinners.remove(BLACK);
            getWinningPlayers();
            return true;
        } else if (allDestinationsHavePlayers) {
            setWinners.add(BLACK);
            getWinningPlayers();
            return true;
        } else if (destinationHasPlayer(players.get(0).location())) { //if mrX is caught
            setWinners.addAll(getPlayers());
            setWinners.remove(BLACK);
            getWinningPlayers();
            return true;
        } else if (currentRoundIndex>=rounds.size()) {
            getWinningPlayers();
            return true;
        } else return false;
    }
    public boolean isGameOver(boolean initialising) {
        if (initialising = true) {
            if (!initialConditionsValid()) {
                return true;
            }
        }
        return false;
    }

    public boolean initialConditionsValid() {
        int i = 0;
        for (Colour c : getPlayers()) {
            ScotlandYardPlayer p = getPlayerFromColour(c).get();
            if (!p.hasTickets(BUS) && !p.hasTickets(TAXI) && !p.hasTickets(UNDERGROUND)
                    && !p.hasTickets(SECRET) && !p.hasTickets(DOUBLE) && getPlayers().size()<3) { //p.isDetective() &&
                i++;
            }
        } if (i!=0) return false;
        else return true;
    }

    //returns the colour of the current player
    @Override
    public Colour getCurrentPlayer() {
        return players.get(currentPlayerIndex).colour();
    }

    //OPTIONAL: returns the player associated with the given colour
    public Optional<ScotlandYardPlayer> getPlayerFromColour(Colour c) {
        for (ScotlandYardPlayer p : players) {
            if (c==p.colour()) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    //ENCAPSULATION: getter for current round index
    @Override
    public int getCurrentRound() {
        return currentRoundIndex;
    }

    //ENCAPSULATION: getter for rounds
    @Override
    public List<Boolean> getRounds() {
        return Collections.unmodifiableList(rounds);
    }

    //ENCAPSULATION: getter for graph
    @Override
    public ImmutableGraph<Integer, Transport> getGraph() {
        return requireNonNull(graph);
    }
}