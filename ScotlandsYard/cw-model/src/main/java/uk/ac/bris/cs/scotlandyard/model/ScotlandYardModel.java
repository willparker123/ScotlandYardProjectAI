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



// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move>, MoveVisitor {

	//initialising variables for model
	public List<Boolean> rounds;
	public ImmutableGraph<Integer, Transport> graph;
	private List<Spectator> spectators = new ArrayList<>();
	public ArrayList<PlayerConfiguration> playerConfigurations = new ArrayList<>();
	public ArrayList<ScotlandYardPlayer> players = new ArrayList<>();
	//index of the playerConfigurations/players array which is the current player
	public int currentPlayerIndex = 0;
	//index of the current round number
	public int currentRoundIndex = 0;
	//true if gameOver
	public boolean gameOverBool = false;
	public boolean waitingForCallback = false;
	public boolean dbMove = false;
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
		//ListIterator<PlayerConfiguration> xs = playerConfigurations.listIterator(1);
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

	//method which turns the player configurations into a list of ScotlandYardPlayer objects
	//Iterator pattern
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

	@Override
	public void registerSpectator(Spectator spectator) {
		Objects.requireNonNull(spectator);
		if (!spectators.contains(spectator)) {
			spectators.add(requireNonNull(spectator));
		}
		else throw new IllegalArgumentException("Invalid Spectator");
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		Objects.requireNonNull(spectator);
		if (!spectators.contains(spectator)) {
			spectators.remove(requireNonNull(spectator));
		}
		else throw new IllegalArgumentException("Invalid Spectator");
	}

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
			if (p.location()==i && p.colour()!=BLACK) return true;
		}
		return false;
	}

	//returns true if the player has at least 1 ticket of the given ticket type
	public boolean playerHasTicketsAvailable(Colour colour, Ticket ticket) {
		int i;
		try {
			i = getPlayerTickets(colour, ticket).get();
		} catch (NoSuchElementException e) {
			i = 0;
		}
		if (i>=1) return true;
		else return false;
	}

	//POLYMORPHISM
	//returns true if the player has at least "n" ticket of the given ticket type
	public boolean playerHasTicketsAvailable(Colour colour, Ticket ticket, int n) {
		int i;
		try {
			i = getPlayerTickets(colour, ticket).get();
		} catch (NoSuchElementException e) {
			i = 0;
		}
		if (i>=n) return true;
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


	//returns a set of valid moves for a given player
	public Set<Move> getValidMoves(ScotlandYardPlayer player) {
		Set<Move> cplayerMoves = new HashSet<>();
		Colour colour = player.colour() ;
		Integer location = player.location() ;

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
						boolean Tickets = (ticket==ticket1 && playerHasTicketsAvailable(colour,ticket,2)) || (ticket != ticket1 && playerHasTicketsAvailable(colour,ticket1)) ;
						TicketMove firstMove = new TicketMove(colour, ticket, destination);
						TicketMove secondMove = new TicketMove(colour, ticket1, destination1);

						if ((destination1 == location || !destinationHasPlayer(destination1)) && Tickets) {
							cplayerMoves.add(new DoubleMove(colour, firstMove, secondMove));
						}
						if ((destination1 == location || !destinationHasPlayer(destination1)) && playerHasTicketsAvailable(colour,SECRET)){
							TicketMove secretFirstMove = new TicketMove(colour,SECRET,destination) ;
							TicketMove secretSecondMove = new TicketMove(colour,SECRET,destination1) ;
							if (playerHasTicketsAvailable(colour,SECRET,2)){
								cplayerMoves.add(new DoubleMove(colour,secretFirstMove,secretSecondMove)) ;
							}
							if (playerHasTicketsAvailable(colour,ticket)){
								cplayerMoves.add(new DoubleMove(colour,firstMove,secretSecondMove)) ;
							}
							if (playerHasTicketsAvailable(colour,ticket1)){
								cplayerMoves.add(new DoubleMove(colour,secretFirstMove,secondMove)) ;
							}
						}

					}
				}
			}

		}
		if (colour.isDetective() && cplayerMoves.isEmpty()) {
			cplayerMoves.add(new PassMove(colour));
		}
		return cplayerMoves;
	}

	@Override
	public void visit(PassMove move) {
	}
	@Override
	public void visit(DoubleMove move) {
		ScotlandYardPlayer cplayer = players.get(currentPlayerIndex);
		cplayer.removeTicket(DOUBLE);
		this.visit(move.firstMove());
		this.visit(move.secondMove());
	}
	@Override
	public void visit(TicketMove move) {
		ScotlandYardPlayer cplayer = players.get(currentPlayerIndex);
		cplayer.removeTicket(move.ticket());
		cplayer.location(move.destination());
		if (getCurrentPlayer()!=BLACK) {
			players.get(0).addTicket(move.ticket());
		}
	}

	//starts the rotation through the players; resets the currentPlayerIndex, provides event handling and tries to
	//make a move from the given list of valid moves
	@Override
	public void startRotate() {
		waitingForCallback = false;
		currentPlayerIndex = 0;
			for (ScotlandYardPlayer p : players) {
				if (!waitingForCallback) {
					waitingForCallback = true;
					Set<Move> cplayerMoves = getValidMoves(p);
					p.player().makeMove(this, p.location(), Objects.requireNonNull(cplayerMoves), this);
					update(this);
				} currentPlayerIndex++;
			}
			update(this);
		}

	//Consumer: ScotlandYardModel is the consumer and this accept() method checks for null and illegal args
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
			update(this, currentRoundIndex);

			/*
			if (rounds.get(currentRoundIndex)) {
				mrXLastLocation = players.get(0).location();
			}
			if (getCurrentPlayer()==BLACK) {
				update(this, mrXLastLocation);
				currentRoundIndex++;
			}
			 */
		}
	}

@Override
public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableList(spectators);
	}

//Iterator: Iterates through the list of ScotlandYardPlayers and creates a list of Colours in the same order
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
		Set<Colour> setWinners = new HashSet<Colour>();
		setWinners = ImmutableSet.copyOf(setWinners);
		return setWinners;
	}

//iterates through 'players' to find a match to the argument Colour
@Override
public Optional<Integer> getPlayerLocation(Colour colour) {
		Iterator<ScotlandYardPlayer> iterator = players.iterator();
		while (iterator.hasNext()) {
		ScotlandYardPlayer x = iterator.next();
		Colour playerColour = x.colour();
		if (playerColour == colour) {
		if (playerColour == BLACK) {
		return Optional.of(0);
		}
		else {
		Optional<Integer> optionalInt = Optional.of(x.location());
		return optionalInt;
		}

		}
		}
		return Optional.empty();
		}

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

@Override
public boolean isGameOver() {
		if (gameOverBool) {
			update(this, getWinningPlayers());
		}

		return gameOverBool;
	}

@Override
	public Colour getCurrentPlayer() {
		return players.get(currentPlayerIndex).colour();
	}

	@Override
	public int getCurrentRound() {
		return currentRoundIndex;
	}

	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public ImmutableGraph<Integer, Transport> getGraph() {
		return requireNonNull(graph);
	}
}