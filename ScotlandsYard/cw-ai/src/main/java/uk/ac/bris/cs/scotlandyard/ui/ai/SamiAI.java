package uk.ac.bris.cs.scotlandyard.ui.ai;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import uk.ac.bris.cs.scotlandyard.ai.ManagedAI;
import uk.ac.bris.cs.scotlandyard.ai.PlayerFactory;
import uk.ac.bris.cs.scotlandyard.ai.ResourceProvider;
import uk.ac.bris.cs.scotlandyard.ai.Visualiser;
import uk.ac.bris.cs.scotlandyard.model.*;
import java.util.*;
import static uk.ac.bris.cs.scotlandyard.model.Colour.*;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;
import uk.ac.bris.cs.gamekit.graph.Node;
import java.lang.Integer;

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
		private int mrXLastLocation = 0;
		private ArrayList<Integer> mrXLastLocations = new ArrayList<>();
		private int thisLastLocation = 0;
		private ArrayList<Integer> thisLastLocations = new ArrayList<>();

		//global coefficients for numValidMoves, numDistanceToEnemy, numDistanceTravelled
		// (0<v<5, 1<d<4, 0<l<2)
		private double cV = 2;
		private double cD = 1;
		private double cL = 2;


		private final Random random = new Random();
		//bool that changes AI logic for a detective/mrX
		private MyPlayer(boolean isMrX) {
			mrXAI = isMrX;
		}

		//COMPARATOR/GENERICS/CALLBACKS: uses scoreMoves to get a list of scores; sort and pick the last/first element
		//									(max/min score for mrX/detective respectively) in moves and play that move.
		@Override
		public void makeMove(ScotlandYardView view, int location, Set<Move> moves,
				Consumer<Move> callback) {
			ArrayList<Double> moveScores = scoreMoves(view, location, moves);
			ArrayList<Double> moveScoresSorted = moveScores;
			Collections.sort(moveScoresSorted);
			int moveIndex;
			moveIndex = moveScores.indexOf(moveScoresSorted.get(0)); //max score move

			//logic to avoid going back and forth between nodes
			try {
				//seemingly better performance if mrX AI can loop (back and forth between nodes)
				if (((TicketMove) new ArrayList<>(moves).get(moveIndex)).destination()==
						thisLastLocations.get(view.getCurrentRound()-2)
						&& new ArrayList<>(moves).get(moveIndex) instanceof TicketMove
						&& view.getCurrentPlayer().isDetective()) {
					for (int i=0;i<moveScores.size();i++) {
						if (((TicketMove) new ArrayList<>(moves).get(moveIndex)).destination()==
								thisLastLocations.get(view.getCurrentRound()-2)) {
							continue;
						} else {
							moveIndex = moveScores.indexOf(moveScoresSorted.get(i)); //second-to-max score move (avoiding loop)
							break;
						}
					}
				}
			} catch (Exception e) { }

			updateLastLocations(view);

			if (view.getCurrentRound()-2<0 && view.getPlayerLocation(BLACK).isPresent()) {
				mrXLastLocation = view.getPlayerLocation(BLACK).get();
			} else if (view.getCurrentRound()-2<0) {
				mrXLastLocation = 0;
			} else mrXLastLocation = mrXLastLocations.get(view.getCurrentRound()-2);

			//EXTRA DETECTIVE AI LOGIC
			if (!mrXAI && mrXLastLocation==view.getPlayerLocation(BLACK).get()) {
				callback.accept(new ArrayList<>(moves).get(scoreMovesMrXSecret(view, location, moves))); //picks max score move
			} else {
				callback.accept(new ArrayList<>(moves).get(moveIndex)); //picks max score move
			}
		}

		private void updateLastLocations(ScotlandYardView view) {
			try {
				while (view.getCurrentRound()!=mrXLastLocations.size()) {
					if (!view.getPlayerLocation(BLACK).isPresent()) mrXLastLocations.add(0);
					else mrXLastLocations.add(view.getPlayerLocation(BLACK).get());
				}
			} catch (NullPointerException e) {
				mrXLastLocations.add(0);
			}

			try {
				if (!view.getPlayerLocation(view.getCurrentPlayer()).isPresent()) thisLastLocations.add(0);
				else thisLastLocations.add(view.getPlayerLocation(view.getCurrentPlayer()).get());
			} catch (NullPointerException e) {
				thisLastLocations.add(0);
			}
		}

		//scores moves for when mrX's last location is his location this round and returns the index of the original
		//		moveset to choose the optimal move from for a detective AI
		private int scoreMovesMrXSecret(ScotlandYardView view, int location, Set<Move> moves) {
			ArrayList<Double> sortedScoresMrX = scoreMoves(view, mrXLastLocation, getValidMoves(BLACK, mrXLastLocation, view));
			Collections.sort(sortedScoresMrX);
			ArrayList<Double> scoresMrX = scoreMoves(view, mrXLastLocation, getValidMoves(BLACK, mrXLastLocation, view));
			ArrayList<Move> arrayMoves = new ArrayList<>(getValidMoves(BLACK, mrXLastLocation, view));
			Move bestMrXMove;
			if (!sortedScoresMrX.isEmpty()) bestMrXMove = arrayMoves.get(scoresMrX.indexOf(sortedScoresMrX.get(0)));
			else bestMrXMove = new PassMove(BLACK);
			ArrayList<Double> scores = new ArrayList<>();
			for (Move m : moves) {
				if (m instanceof TicketMove) {
					if (bestMrXMove instanceof TicketMove) {
						scores.add(criticalPath(view, ((TicketMove) bestMrXMove).destination(), location));
					} else if (bestMrXMove instanceof DoubleMove) {
						scores.add(criticalPath(view, ((DoubleMove) bestMrXMove).secondMove().destination(), location));
					} else scores.add(100.0);
				} else scores.add(100.0);
			}
			ArrayList<Double> sortedScores = new ArrayList<>(scores);
			Collections.sort(sortedScores);
			return scores.indexOf(sortedScores.get(0));
		}

		//scores moves (high = best move, low = worst move)
		private ArrayList<Double> scoreMoves(ScotlandYardView view, int location, Set<Move> moves) {
			ArrayList<Double> scores = new ArrayList<>();
			//for each of the moves, score them and add the score to 'scores'
			Iterator<Move> iterator = moves.iterator();
			while (iterator.hasNext()) {
				scores.add(score(iterator.next(), view));
			}

			return scores;
		}

		private double score(Move m, ScotlandYardView view) {
			//visitor
			if (m instanceof TicketMove) return score((TicketMove) m, view);
			else if (m instanceof PassMove) return score((PassMove) m, view);
			else if (m instanceof DoubleMove) return score((DoubleMove) m, view);
			else return 0.0;
		}

		//good score: far from detectives, many validMoves with target node
		private double score(TicketMove m, ScotlandYardView view) {
			int totalDistance = 0;
			int totalValidMoves = 0;
			double distanceFromStartNode = criticalPath(view, m.destination(), view.getPlayerLocation(m.colour()).get());

			try {
				if (m.colour().isMrX()) { //gets the number of valid moves from the move's destination (one-step ahead)
					totalValidMoves = getValidMoves(BLACK, m.destination(), view).size();
				} else totalValidMoves = getValidMoves(m.colour(), m.destination(), view).size();
			} catch (Exception e) {
				totalValidMoves = 1;
			}

			//for all players, find the distance to mrX from the player and total these distances up
			if (m.colour().isMrX()) { //AI MRX LOGIC
				List<Colour> cs = view.getPlayers();
				for (Colour c : cs) {
					//skip players with no location
					if (!view.getPlayerLocation(c).isPresent()) continue;
					else {
						//finds critical path to mrX from the player
						totalDistance+=criticalPath(view, m.destination(), view.getPlayerLocation(c).get());
					}
				}
			}	//AI DETECTIVE LOGIC
			else { //for detectives; find the distance to mrX
					if (!view.getPlayerLocation(BLACK).isPresent()) totalDistance+=0;
					else {
						//if mrx has a location
						if (view.getPlayerLocation(view.getPlayers().get(0)).isPresent()) {
							totalDistance+=criticalPath(view, m.destination(), mrXLastLocation);
						} else { //mrx no location
							totalDistance+=1;
						}
					}
			}

			if (m.colour().isMrX()) {
				return (cV*cV*totalValidMoves)+(cD*totalDistance)+(cL*distanceFromStartNode);
			} else {
				return (cV*totalValidMoves)-(cD*totalDistance)+(cL*distanceFromStartNode);
			}
		}
		private double score(DoubleMove m, ScotlandYardView view) {
			return score(m.secondMove(), view);
			//good score: far from detectives, many validMoves with target node
		}
		private double score(PassMove m, ScotlandYardView view) {
			return 0;
			//good score: far from detectives, many validMoves with target node
		}

		private double criticalPath(ScotlandYardView view, int x, int y) {
			double max = 0;
				for (Edge<Integer, Transport> e: view.getGraph().getEdgesFrom(view.getGraph().getNode(x))) {
					int s = 0;
					while (s<max || s==0) {
						if (x==y) s = 0;
						if (e.destination().equals(view.getGraph().getNode(y))) s++;
						else {
								//int ss = criticalPath(view, e.destination().value(), y);
								s+=2;
						}
					}
					if (s>max) max = s;
				}
				return max;
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
