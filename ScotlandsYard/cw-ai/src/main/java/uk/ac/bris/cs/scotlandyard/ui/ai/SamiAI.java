package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import uk.ac.bris.cs.scotlandyard.ai.ManagedAI;
import uk.ac.bris.cs.scotlandyard.ai.PlayerFactory;
import uk.ac.bris.cs.scotlandyard.model.Colour;
import uk.ac.bris.cs.scotlandyard.model.Move;
import uk.ac.bris.cs.scotlandyard.model.DoubleMove;
import uk.ac.bris.cs.scotlandyard.model.PassMove;
import uk.ac.bris.cs.scotlandyard.model.TicketMove;
import uk.ac.bris.cs.scotlandyard.model.Player;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYardView;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYardModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import static uk.ac.bris.cs.scotlandyard.model.Colour.*;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;

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

		private MyPlayer(boolean isMrX) {
			mrXAI = isMrX;
		}

		@Override
		public void makeMove(ScotlandYardView view, int location, Set<Move> moves,
				Consumer<Move> callback) {
			ArrayList<int> moveScores = scoreMoves(view, location, moves);
			ArrayList<int> moveScoresSorted = moveScores.sort();
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
		private ArrayList<int> scoreMoves(ScotlandYardView view, int location, Set<Move> moves) {
			ArrayList<int> scores = new ArrayList<>();
			//for each of the moves, score them and add the score to 'scores'
			Iterator<Move> iterator = moves.iterator();
			while (iterator.hasNext()) {
				scores.add(score(iterator.next(), view));
			}
			return scores;
		}

		private ArrayList<int> sortScores(ArrayList<int> scores) {
			return scores.sort();
		}

		//good score: far from detectives, many validMoves with target node
		private int score(TicketMove m, ScotlandYardView view) {
			int totalDistance = 0;
			int totalValidMoves = 0;
			//for all players, find the distance to mrX from the player
			if (mrXAI) {
				List<Colour> cs = view.getPlayers();
				Iterator<Move> iterator = cs.iterator();
				while (iterator.hasNext()) {
					//skip players with no location
					if (view.getPlayerLocation(iterator.next())==Optional.empty()) continue;
					else {
						//finds critical path to mrX from the player
						totalDistance+=criticalPath(m.destination(), view.getPlayerLocation(iterator.next()).get());
					}
				}
			} else {
				List<Colour> cs = view.getPlayers();
				//skip players with no location
				if (view.getPlayerLocation(iterator.next())==Optional.empty()) continue;
				else {
					//finds critical path to mrX from the move's destination
					totalDistance+=criticalPath(m.destination(), view.getPlayerLocation(cs.get(0)).get());
				}
			}

			try {
				if (mrXAI) {
					totalValidMoves = getValidMoves(BLACK, m.destination()).size();
				} else totalValidMoves = getValidMoves(RED, m.destination()).size();
			} catch (Exception e) {
				totalValidMoves = 1;
			}

			if (mrXAI) return totalDistance*totalValidMoves;
			else return totalDistance/totalValidMoves;
		}
		private int score(DoubleMove m, ScotlandYardView view) {
			//good score: far from detectives, many validMoves with target node
		}
		private int score(PassMove m, ScotlandYardView view) {
			//good score: far from detectives, many validMoves with target node
		}

		private int criticalPath(int x, int y) {
			return x-y;
		}

		public Set<Move> getValidMoves(Colour colour, Integer location) {
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

		public boolean destinationHasPlayer(int i) {
			for (ScotlandYardPlayer p : players) {
				if (p.location()==i && p.colour().isDetective()) return true;
			}
			return false;
		}

		public boolean playerHasTicketsAvailable(Colour colour, Ticket ticket) {
			int i;
			try {
				i = getPlayerTickets(colour, ticket).get();
			} catch (NoSuchElementException e) {
				i = 0;
			} if (i>=1) return true;
			else return false;
		}
	}
}
