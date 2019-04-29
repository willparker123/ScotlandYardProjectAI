package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import uk.ac.bris.cs.scotlandyard.ai.ManagedAI;
import uk.ac.bris.cs.scotlandyard.ai.PlayerFactory;
import uk.ac.bris.cs.scotlandyard.model.*;
import java.util.*;
import static uk.ac.bris.cs.scotlandyard.model.Colour.*;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Node;

@ManagedAI("SAMI")
public class SamiAI implements PlayerFactory {

	@Override
	public Player createPlayer(Colour colour) {
		boolean isMrX = false;
		if (colour.isMrX()) {
			isMrX = true;
		} else isMrX = false;
		return new MyPlayer(isMrX);
	}

	private static class MyPlayer implements Player {
		//executes different move decider logic depending on if this is true or false
		private final boolean mrXAI;

		private final Random random = new Random();
		private MyPlayer(boolean isMrX) {
			mrXAI = isMrX;
		}

		@Override
		public void makeMove(ScotlandYardView view, int location, Set<Move> moves,
				Consumer<Move> callback) {
			ArrayList<Integer> moveScores = scoreMoves(view, location, moves);
			ArrayList<Integer> moveScoresSorted = moveScores;
			Collections.sort(moveScoresSorted);
			int moveIndex;

			if (mrXAI) {
				moveIndex = moveScores.indexOf(moveScoresSorted.get(0)); //max score move
			} else {
				moveIndex = moveScores.indexOf(moveScoresSorted.get(moveScores.size()-1)); //min score move
			}

			// picks a move
			callback.accept(new ArrayList<>(moves).get(moveIndex));
		}

		//scores moves (high = best move, low = worst move)
		private ArrayList<Integer> scoreMoves(ScotlandYardView view, int location, Set<Move> moves) {
			ArrayList<Integer> scores = new ArrayList<>();
			//for each of the moves, score them and add the score to 'scores'
			Iterator<Move> iterator = moves.iterator();
			while (iterator.hasNext()) {
				scores.add(score(iterator.next(), view));
			}
			return scores;
		}

		//TODO
		private ArrayList<Integer> sortScores(ArrayList<Integer> scores) {
			return scores;
		}

		private Integer score(Move m, ScotlandYardView view) {
			if (m instanceof TicketMove) return score((TicketMove) m, view);
			else if (m instanceof PassMove) return score((PassMove) m, view);
			else if (m instanceof DoubleMove) return score((DoubleMove) m, view);
			else return 0;
		}
		//good score: far from detectives, many validMoves with target node
		private Integer score(TicketMove m, ScotlandYardView view) {
			int totalDistance = 0;
			int totalValidMoves = 0;
			//for all players, find the distance to mrX from the player
			if (mrXAI) {
				List<Colour> cs = view.getPlayers();
				ListIterator<Colour> iterator = cs.listIterator();
				while (iterator.hasNext()) {
					//skip players with no location
					if (!view.getPlayerLocation(iterator.next()).isPresent()) continue;
					else {
						//finds critical path to mrX from the player
						totalDistance+=criticalPath(m.destination(), view.getPlayerLocation(iterator.next()).get());
					}
				}
			} else {
				List<Colour> cs = view.getPlayers();
				ListIterator<Colour> iterator = cs.listIterator();
				while (iterator.hasNext()) {
					//skip players with no location
					if (!view.getPlayerLocation(iterator.next()).isPresent()) continue;
					else {
						//finds critical path to mrX from the move's destination
						totalDistance+=criticalPath(m.destination(), view.getPlayerLocation(cs.get(0)).get());
					}
				}
			}

			try {
				if (mrXAI) {
					totalValidMoves = getValidMoves(BLACK, m.destination(), view).size();
				} else totalValidMoves = getValidMoves(m.ticket(), m.destination(), view).size();
			} catch (Exception e) {
				totalValidMoves = 1;
			}

			if (mrXAI) return Integer.valueOf(totalDistance*totalValidMoves);
			else return Integer.valueOf(totalDistance/totalValidMoves);
		}
		//TODO
		private int score(DoubleMove m, ScotlandYardView view) {
			return random.nextInt(10000);
			//good score: far from detectives, many validMoves with target node
		}
		//TODO
		private int score(PassMove m, ScotlandYardView view) {
			return random.nextInt(10000);
			//good score: far from detectives, many validMoves with target node
		}

		//TODO
		private int criticalPath(int x, int y) {
			return x-y;
		}

		public int roundRemaining(ScotlandYardView view) {
			return view.getRounds().size() - view.getCurrentRound();
		}

		public Set<Move> getValidMoves(Colour colour, Integer location, ScotlandYardView view) {
			Set<Move> cplayerMoves = new HashSet<>();
			Node<Integer> nodeL = view.getGraph().getNode(location);
			Collection<Edge<Integer, Transport>> e;

			if (nodeL == null) {
				e = view.getGraph().getEdges();
			} else {
				e = view.getGraph().getEdgesFrom(Objects.requireNonNull(nodeL));
			}

			for (Edge<Integer, Transport> edge : e) {
				Integer destination = edge.destination().value();
				Ticket ticket = Ticket.fromTransport(edge.data());
				//if the reachable nodes don't have a player on them and the player has available tickets,
				// add this node as a possible TicketMove
				if (!destinationHasPlayer(view,destination)) {
					if (playerHasTicketsAvailable(view,colour, ticket)) {
						cplayerMoves.add(new TicketMove(colour, ticket, destination));
					}
					if (playerHasTicketsAvailable(view,colour, SECRET)) {
						cplayerMoves.add(new TicketMove(colour, SECRET, destination));
					}
					if (playerHasTicketsAvailable(view,colour, DOUBLE) && roundRemaining(view) >= 2) {
						Node<Integer> nodeR = view.getGraph().getNode(destination);
						Collection<Edge<Integer, Transport>> d;
						if (nodeR == null) {
							d = view.getGraph().getEdges();
						} else {
							d = view.getGraph().getEdgesFrom(Objects.requireNonNull(nodeR));
						}
						for (Edge<Integer, Transport> edge1 : d) {
							Integer destination1 = edge1.destination().value();
							Ticket ticket1 = Ticket.fromTransport(edge1.data());
							boolean tickets = (ticket==ticket1 && playerHasTicketsAvailable(view,colour,ticket,2))
									|| (ticket != ticket1 && playerHasTicketsAvailable(view,colour,ticket1));
							TicketMove firstMove = new TicketMove(colour, ticket, destination);
							TicketMove secondMove = new TicketMove(colour, ticket1, destination1);

							if ((destination1 == location || !destinationHasPlayer(view,destination1)) && tickets) {
								cplayerMoves.add(new DoubleMove(colour, firstMove, secondMove));
							} if ((destination1 == location || !destinationHasPlayer(view,destination1))
									&& playerHasTicketsAvailable(view,colour,SECRET)) {
								TicketMove secretFirstMove = new TicketMove(colour,SECRET,destination);
								TicketMove secretSecondMove = new TicketMove(colour,SECRET,destination1);
								if (playerHasTicketsAvailable(view,colour,SECRET,2)) {
									cplayerMoves.add(new DoubleMove(colour,secretFirstMove,secretSecondMove));
								} if (playerHasTicketsAvailable(view,colour,ticket)) {
									cplayerMoves.add(new DoubleMove(colour,firstMove,secretSecondMove));
								} if (playerHasTicketsAvailable(view,colour,ticket1)) {
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

		public boolean destinationHasPlayer(ScotlandYardView view, int i) {
			for (Colour p : view.getPlayers()) {
				if (view.getPlayerLocation(p).get()==i && p.isDetective()) return true;
			}
			return false;
		}

		public boolean playerHasTicketsAvailable(ScotlandYardView view, Colour colour, Ticket ticket) {
			int i;
			try {
				i = view.getPlayerTickets(colour, ticket).get();
			} catch (NoSuchElementException e) {
				i = 0;
			} if (i>=1) return true;
			else return false;
		}
		public boolean playerHasTicketsAvailable(ScotlandYardView view, Colour colour, Ticket ticket, int n) {
			int i;
			try {
				i = view.getPlayerTickets(colour, ticket).get();
			} catch (NoSuchElementException e) {
				i = 0;
			} if (i>=n) return true;
			else return false;
		}
	}
}
