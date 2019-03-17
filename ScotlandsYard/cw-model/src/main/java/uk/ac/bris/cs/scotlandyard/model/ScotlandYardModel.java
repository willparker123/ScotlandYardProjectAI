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
import java.lang.Iterable;
import java.util.Iterator;
import uk.ac.bris.cs.gamekit.graph.Graph;

// TODO implement all methods and pass all tests
public class ScotlandYardModel implements ScotlandYardGame {

	//initialising variables for model
	public List<Boolean> rounds;
	public static Graph<Integer, Transport> graph;
	public static Collection<Spectator> spectators = Collections.emptyList();
	public ArrayList<PlayerConfiguration> players = new ArrayList<>();

	//initialising variables for duplicate checksum
	private Set<Integer> setLocations = new HashSet<>();
	private Set<Colour> setColours = new HashSet<>();



	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {

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
			this.players.add(0,requireNonNull(mrX));
			this.players.add(1,requireNonNull(firstDetective));

			for (PlayerConfiguration detective : restOfTheDetectives) {
				this.players.add(requireNonNull(detective));
			}

			spectators.forEach(Objects::requireNonNull);
		}
		checkDuplicates();
		checkTickets();
	}

	//checks for duplicate locations and colours in the "players" set
	public void checkDuplicates() {
		for (PlayerConfiguration player : players) {
			if (setLocations.contains(player.location)) throw new IllegalArgumentException("Duplicate Player Location");
			setLocations.add(player.location);
			if (setColours.contains(player.colour)) throw new IllegalArgumentException("Duplicate Player Colour");
			setColours.add(player.colour);
		}
	}

	//checks for invalid tickets and mappings
	public void checkTickets() {
		//ListIterator<PlayerConfiguration> xs = players.listIterator(1);
		for (int i=0;i<players.size();i++) {
			//all keys in the 'tickets' Map
			Set<Ticket> tickets = players.get(i).tickets.keySet();

			if (tickets.size()!=5) {
				throw new IllegalArgumentException("Map has missing tickets");
			}
			if (players.get(i).colour==BLACK && (players.get(i).tickets.get(TAXI)!=4
					|| players.get(i).tickets.get(UNDERGROUND)!=3 || players.get(i).tickets.get(BUS)!=3
					|| players.get(i).tickets.get(SECRET)!=5 || players.get(i).tickets.get(DOUBLE)!=2)) {
				throw new IllegalArgumentException("Player MrX has missing tickets");
			}
			if (players.get(i).colour!=BLACK && (players.get(i).tickets.get(TAXI)!=11
					|| players.get(i).tickets.get(UNDERGROUND)!=4 || players.get(i).tickets.get(BUS)!=8
					|| players.get(i).tickets.get(DOUBLE)!=0 || players.get(i).tickets.get(SECRET)!=0)) {
				throw new IllegalArgumentException("Player Detective has missing tickets");
			}
		}
	}

	@Override
	public void registerSpectator(Spectator spectator) {

		//for (Spectator s : )
		throw new IllegalArgumentException();
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public void startRotate() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Collection<Spectator> getSpectators() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public List<Colour> getPlayers() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public boolean isGameOver() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Colour getCurrentPlayer() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public int getCurrentRound() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public List<Boolean> getRounds() {
		// TODO
		throw new RuntimeException("Implement me");
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		// TODO
		throw new RuntimeException("Implement me");
	}

}
