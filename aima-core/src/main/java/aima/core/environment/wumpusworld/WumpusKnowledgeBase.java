package aima.core.environment.wumpusworld;

import aima.core.logic.propositional.inference.DPLL;
import aima.core.logic.propositional.inference.OptimizedDPLL;
import aima.core.logic.propositional.kb.KnowledgeBase;
import aima.core.logic.propositional.parsing.ast.ComplexSentence;
import aima.core.logic.propositional.parsing.ast.Connective;
import aima.core.logic.propositional.parsing.ast.PropositionSymbol;
import aima.core.logic.propositional.parsing.ast.Sentence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Knowledge base tailored to the Wumpus World environment.
 *
 * @author Ciaran O'Reilly
 * @author Federico Baron
 * @author Alessandro Daniele
 * @author Ruediger Lunde
 */
public class WumpusKnowledgeBase extends KnowledgeBase {
    public static final String LOCATION = "L";
    public static final String LOCATION_VISITED = "LV"; // tuning...
    public static final String BREEZE = "B";
    public static final String STENCH = "S";
    public static final String PIT = "P";
    public static final String WUMPUS = "W";
    public static final String WUMPUS_ALIVE = "WumpusAlive";
    public static final String HAVE_ARROW = "HaveArrow";
    public static final String FACING_NORTH = AgentPosition.Orientation.FACING_NORTH.name();
    public static final String FACING_SOUTH = AgentPosition.Orientation.FACING_SOUTH.name();
    public static final String FACING_EAST = AgentPosition.Orientation.FACING_EAST.name();
    public static final String FACING_WEST = AgentPosition.Orientation.FACING_WEST.name();
    public static final String PERCEPT_STENCH = "Stench";
    public static final String PERCEPT_BREEZE = "Breeze";
    public static final String PERCEPT_GLITTER = "Glitter";
    public static final String PERCEPT_BUMP = "Bump";
    public static final String PERCEPT_SCREAM = "Scream";
    public static final String ACTION_FORWARD = WumpusAction.FORWARD.name();
    public static final String ACTION_SHOOT = WumpusAction.SHOOT.name();
    public static final String ACTION_TURN_LEFT = WumpusAction.TURN_LEFT.name();
    public static final String ACTION_TURN_RIGHT = WumpusAction.TURN_RIGHT.name();
    public static final String OK_TO_MOVE_INTO = "OK";
    //
    private int caveXDimension;
    private int caveYDimension;
    private AgentPosition start;
    private DPLL dpll;
    private boolean disableNavSentences;

    public WumpusKnowledgeBase(int caveDimensions) {
        this(new OptimizedDPLL(), caveDimensions);
    }

    public WumpusKnowledgeBase(DPLL dpll, int caveDimensions) {
        this(dpll, caveDimensions, new AgentPosition(1, 1, AgentPosition.Orientation.FACING_NORTH));
    }

    /**
     * Create a Knowledge Base that contains the atemporal "wumpus physics" and
     * temporal rules with time zero.
     *
     * @param dpll               the dpll implementation to use for answering 'ask' queries.
     * @param caveDimensions     x and y dimensions of the wumpus world's cave.
     */
    public WumpusKnowledgeBase(DPLL dpll, int caveDimensions, AgentPosition start) {

        this.start = start;
        this.dpll = dpll;
        caveXDimension = caveDimensions;
        caveYDimension = caveDimensions;

        //
        // 7.7.1 - The current state of the World
        // The agent knows that the starting square contains no pit
        tell(new ComplexSentence(Connective.NOT, newSymbol(PIT, start.getX(), start.getY())));
        // and no wumpus.
        tell(new ComplexSentence(Connective.NOT, newSymbol(WUMPUS, start.getX(), start.getY())));

        // Atemporal rules about breeze and stench
        // For each square, the agent knows that the square is breezy
        // if and only if a neighboring square has a pit; and a square
        // is smelly if and only if a neighboring square has a wumpus.
        for (int y = 1; y <= caveYDimension; y++) {
            for (int x = 1; x <= caveXDimension; x++) {

                List<PropositionSymbol> pitsIn = new ArrayList<>();
                List<PropositionSymbol> wumpsIn = new ArrayList<>();

                if (x > 1) { // West room exists
                    pitsIn.add(newSymbol(PIT, x - 1, y));
                    wumpsIn.add(newSymbol(WUMPUS, x - 1, y));
                }
                if (y < caveYDimension) { // North room exists
                    pitsIn.add(newSymbol(PIT, x, y + 1));
                    wumpsIn.add(newSymbol(WUMPUS, x, y + 1));
                }
                if (x < caveXDimension) { // East room exists
                    pitsIn.add(newSymbol(PIT, x + 1, y));
                    wumpsIn.add(newSymbol(WUMPUS, x + 1, y));
                }
                if (y > 1) { // South room exists
                    pitsIn.add(newSymbol(PIT, x, y - 1));
                    wumpsIn.add(newSymbol(WUMPUS, x, y - 1));
                }

                tell(new ComplexSentence
                        (newSymbol(BREEZE, x, y), Connective.BICONDITIONAL, Sentence.newDisjunction(pitsIn)));
                tell(new ComplexSentence
                        (newSymbol(STENCH, x, y), Connective.BICONDITIONAL, Sentence.newDisjunction(wumpsIn)));
            }
        }

        // The Wumpus will never be located in a pit.
        for (int y = 1; y <= caveYDimension; y++)
            for (int x = 1; x <= caveXDimension; x++)
                tell(new ComplexSentence(newSymbol(WUMPUS, x, y), Connective.IMPLICATION,
                        new ComplexSentence(Connective.NOT, newSymbol(PIT, x, y))));

        // The agent also knows there is exactly one wumpus. This is represented
        // in two parts. First, we have to say that there is at least one wumpus
        List<PropositionSymbol> wumpsAtLeast = new ArrayList<>();
        for (int x = 1; x <= caveXDimension; x++)
            for (int y = 1; y <= caveYDimension; y++)
                wumpsAtLeast.add(newSymbol(WUMPUS, x, y));

        tell(Sentence.newDisjunction(wumpsAtLeast));

        // Then, we have to say that there is at most one wumpus.
        // For each pair of locations, we add a sentence saying
        // that at least one of them must be wumpus-free.
        int numRooms = (caveXDimension * caveYDimension);
        for (int i = 0; i < numRooms; i++) {
            for (int j = i + 1; j < numRooms; j++) {
                tell(new ComplexSentence(Connective.OR,
                        new ComplexSentence
                                (Connective.NOT, newSymbol(WUMPUS, (i / caveXDimension) + 1, (i % caveYDimension) + 1)),
                        new ComplexSentence
                                (Connective.NOT, newSymbol(WUMPUS, (j / caveXDimension) + 1, (j % caveYDimension) + 1))));
            }
        }
    }

    /**
     * Disables creation of computational expensive temporal navigation sentences.
     */
    public void disableNavSentences() {
        disableNavSentences = true;
    }

    public AgentPosition askCurrentPosition(int t) {

        // There seems to be a bug in OptimizedDPLL (incorrect position computation):
        // Call with: WumpusAgentDemo, 2x2 cave, OptimizedDPLL (HybridWumpusAgend-Constructor)
        /*
        if (t == 4) { // todo
			System.out.println("Ask L_3_1_1: " + ask(newSymbol(LOCATION, 3, 1, 1))); // false
			System.out.println("Ask L_3_1_2: " + ask(newSymbol(LOCATION, 3, 1, 2))); // true
			System.out.println("Ask L_3_2_1: " + ask(newSymbol(LOCATION, 3, 2, 1))); // false
			System.out.println("Ask L_3_2_2: " + ask(newSymbol(LOCATION, 3, 2, 2))); // false
			System.out.println("Ask North_3: " + ask(newSymbol(FACING_NORTH, 3))); // false
			System.out.println("Ask South_3: " + ask(newSymbol(FACING_SOUTH, 3))); // false
			System.out.println("Ask East_3: " + ask(newSymbol(FACING_EAST, 3))); // true
			System.out.println("Ask West_3: " + ask(newSymbol(FACING_WEST, 3))); // false

			System.out.println("Ask L_4_1_1: " + ask(newSymbol(LOCATION, 4, 1, 1)) + " !"); // true (incorrect)
			System.out.println("Ask L_4_2_2: " + ask(newSymbol(LOCATION, 4, 2, 2)) + " !"); // true (correct)
			System.out.println("Ask North_4: " + ask(newSymbol(FACING_NORTH, 4))); // false
			System.out.println("Ask South_4: " + ask(newSymbol(FACING_SOUTH, 4))); // false
			System.out.println("Ask East_4: " + ask(newSymbol(FACING_EAST, 4))); // true
			System.out.println("Ask West_4: " + ask(newSymbol(FACING_WEST, 4))); // false

			System.out.println("Ask L_5_1_1: " + ask(newSymbol(LOCATION, 5, 1, 1))); // false
			System.out.println("Ask L_5_1_2: " + ask(newSymbol(LOCATION, 5, 1, 2))); // false
			System.out.println("Ask L_5_2_1: " + ask(newSymbol(LOCATION, 5, 2, 1))); // false
			System.out.println("Ask L_5_2_2: " + ask(newSymbol(LOCATION, 5, 2, 2))); // false

			// L_4_1_1 is only constrained by:
			// L_4_1_1 <=> L_3_1_1 & (~FORWARD_3 | Bump_4) | L_3_1_2 & FACING_SOUTH_3 & FORWARD_3 | L_3_2_1 & FACING_WEST_3 & FORWARD_3
		}
		*/

        int locX = -1, locY = -1;
        for (int x = 1; x <= getCaveXDimension() && locX == -1; x++) {
            for (int y = 1; y <= getCaveYDimension() && locY == -1; y++) {
                if (ask(newSymbol(LOCATION, t, x, y))) {
                    locX = x;
                    locY = y;
                }
            }
        }
        if (locX == -1 || locY == -1)
            throw new IllegalStateException("Inconsistent KB, unable to determine current room position.");

        AgentPosition current = null;
        if (ask(newSymbol(FACING_NORTH, t)))
            current = new AgentPosition(locX, locY, AgentPosition.Orientation.FACING_NORTH);
        else if (ask(newSymbol(FACING_SOUTH, t)))
            current = new AgentPosition(locX, locY, AgentPosition.Orientation.FACING_SOUTH);
        else if (ask(newSymbol(FACING_EAST, t)))
            current = new AgentPosition(locX, locY, AgentPosition.Orientation.FACING_EAST);
        else if (ask(newSymbol(FACING_WEST, t)))
            current = new AgentPosition(locX, locY, AgentPosition.Orientation.FACING_WEST);
        else
            throw new IllegalStateException("Inconsistent KB, unable to determine current room orientation.");

        return current;
    }

    // safe <- {[x, y] : ASK(KB, OK<sup>t</sup><sub>x,y</sub>) = true}
    public Set<Room> askSafeRooms(int t) {
        Set<Room> safe = new LinkedHashSet<>();
        for (int x = 1; x <= getCaveXDimension(); x++) {
            for (int y = 1; y <= getCaveYDimension(); y++) {
                if (ask(newSymbol(OK_TO_MOVE_INTO, t, x, y))) {
                    safe.add(new Room(x, y));
                }
            }
        }
        return safe;
    }

    public boolean askGlitter(int t) {
        return ask(newSymbol(PERCEPT_GLITTER, t));
    }

    // unvisited <- {[x, y] : ASK(KB, L<sup>t'</sup><sub>x,y</sub>) = false for all t' &le; t}
    public Set<Room> askUnvisitedRooms(int t) {
        Set<Room> unvisited = new LinkedHashSet<>();

        for (int x = 1; x <= getCaveXDimension(); x++) {
            for (int y = 1; y <= getCaveYDimension(); y++) {
                if (!ask(newSymbol(LOCATION_VISITED, x, y)))
                    unvisited.add(new Room(x, y)); // i.e. is false for all t' <= t

//				to slow:
//				for (int tPrime = 0; tPrime <= t; tPrime++) {
//					if (ask(newSymbol(LOCATION, tPrime, x, y)))
//						break; // i.e. is not false for all t' <= t
//					if (tPrime == t)
//						unvisited.add(new Room(x, y)); // i.e. is false for all t' <= t
//				}
            }
        }

        return unvisited;
    }

    public boolean askHaveArrow(int t) {
        return ask(newSymbol(HAVE_ARROW, t));
    }

    // possible_wumpus <- {[x, y] : ASK(KB, ~W<sub>x,y</sub>) = false}
    public Set<Room> askPossibleWumpusRooms(int t) {
        Set<Room> possible = new LinkedHashSet<>();
        for (int x = 1; x <= getCaveXDimension(); x++) {
            for (int y = 1; y <= getCaveYDimension(); y++) {
                if (!ask(new ComplexSentence(Connective.NOT, newSymbol(WUMPUS, x, y)))) {
                    possible.add(new Room(x, y));
                }
            }
        }
        return possible;
    }

    // not_unsafe <- {[x, y] : ASK(KB, ~OK<sup>t</sup><sub>x,y</sub>) = false}
    public Set<Room> askNotUnsafeRooms(int t) {
        Set<Room> notUnsafe = new LinkedHashSet<>();
        for (int x = 1; x <= getCaveXDimension(); x++) {
            for (int y = 1; y <= getCaveYDimension(); y++) {
                if (!ask(new ComplexSentence(Connective.NOT, newSymbol(OK_TO_MOVE_INTO, t, x, y)))) {
                    notUnsafe.add(new Room(x, y));
                }
            }
        }
        return notUnsafe;
    }

    public boolean askOK(int t, int x, int y) {
        return ask(newSymbol(OK_TO_MOVE_INTO, t, x, y));
    }

    public boolean ask(Sentence query) {
        return dpll.isEntailed(this, query);
    }

    public int getCaveXDimension() {
        return caveXDimension;
    }

    public void setCaveXDimension(int caveXDimension) {
        this.caveXDimension = caveXDimension;
    }

    public int getCaveYDimension() {
        return caveYDimension;
    }

    public void setCaveYDimension(int caveYDimension) {
        this.caveYDimension = caveYDimension;
    }

    /**
     * Add to KB sentences that describe the action a
     *
     * @param a action that must be added to KB
     * @param t current time
     */
    public void makeActionSentence(WumpusAction a, int t) {
        for (WumpusAction action : WumpusAction.values()) {
            if (action.equals(a))
                tell(newSymbol(action.name(), t));
            else
                tell(new ComplexSentence(Connective.NOT, newSymbol(action.name(), t)));
        }
    }

    /**
     * Add to KB sentences that describe the perception p
     * (only about the current time).
     *
     * @param p    perception that must be added to KB
     * @param time current time
     */
    public void makePerceptSentence(WumpusPercept p, int time) {
        if (p.isStench())
            tell(newSymbol(PERCEPT_STENCH, time));
        else
            tell(new ComplexSentence(Connective.NOT, newSymbol(PERCEPT_STENCH, time)));

        if (p.isBreeze())
            tell(newSymbol(PERCEPT_BREEZE, time));
        else
            tell(new ComplexSentence(Connective.NOT, newSymbol(PERCEPT_BREEZE, time)));

        if (p.isGlitter())
            tell(newSymbol(PERCEPT_GLITTER, time));
        else
            tell(new ComplexSentence(Connective.NOT, newSymbol(PERCEPT_GLITTER, time)));

        if (p.isBump())
            tell(newSymbol(PERCEPT_BUMP, time));
        else
            tell(new ComplexSentence(Connective.NOT, newSymbol(PERCEPT_BUMP, time)));

        if (p.isScream())
            tell(newSymbol(PERCEPT_SCREAM, time));
        else
            tell(new ComplexSentence(Connective.NOT, newSymbol(PERCEPT_SCREAM, time)));
    }

    /**
     * When navigation sentence creation is disabled, the knowledge base must be informed about agent positions
     * using this method.
     */
    public void makePositionSentence(AgentPosition position, int t) {
        tell(newSymbol(LOCATION, t, position.getX(), position.getY()));
        tell(newSymbol(LOCATION_VISITED, position.getX(), position.getY()));
    }

    /**
     * TELL the KB the temporal "physics" sentences for time t
     *
     * @param t current time step.
     */
    public void tellTemporalPhysicsSentences(int t) {
        if (t == 0) {
            // temporal rules at time zero
            tell(newSymbol(LOCATION, 0, start.getX(), start.getY()));
            tell(newSymbol(start.getOrientation().name(), 0));
            tell(newSymbol(HAVE_ARROW, 0));
            tell(newSymbol(WUMPUS_ALIVE, 0));
            // Optimization to make questions about unvisited locations faster
            tell(newSymbol(LOCATION_VISITED, start.getX(), start.getY()));
        }

        // We can connect stench and breeze percepts directly
        // to the properties of the squares where they are experienced
        // through the location fluent as follows. For any time step t
        // and any square [x,y], we assert
        for (int x = 1; x <= caveXDimension; x++) {
            for (int y = 1; y <= caveYDimension; y++) {
                tell(new ComplexSentence(
                        newSymbol(LOCATION, t, x, y),
                        Connective.IMPLICATION,
                        new ComplexSentence(newSymbol(PERCEPT_BREEZE, t), Connective.BICONDITIONAL, newSymbol(BREEZE, x, y))));

                tell(new ComplexSentence(
                        newSymbol(LOCATION, t, x, y),
                        Connective.IMPLICATION,
                        new ComplexSentence(newSymbol(PERCEPT_STENCH, t), Connective.BICONDITIONAL, newSymbol(STENCH, x, y))));
            }
        }

        for (int x = 1; x <= caveXDimension; x++) {
            for (int y = 1; y <= caveYDimension; y++) {
                // The most important question for the agent is whether
                // a square is OK to move into, that is, the square contains
                // no pit nor live wumpus.
                tell(new ComplexSentence(
                        newSymbol(OK_TO_MOVE_INTO, t, x, y),
                        Connective.BICONDITIONAL,
                        new ComplexSentence(
                                new ComplexSentence(Connective.NOT, newSymbol(PIT, x, y)),
                                Connective.AND,
                                new ComplexSentence(Connective.NOT,
                                        new ComplexSentence(
                                                newSymbol(WUMPUS, x, y),
                                                Connective.AND,
                                                newSymbol(WUMPUS_ALIVE, t))))));
            }
        }

        // Rule about the arrow
        tell(new ComplexSentence(
                newSymbol(HAVE_ARROW, t + 1),
                Connective.BICONDITIONAL,
                new ComplexSentence(
                        newSymbol(HAVE_ARROW, t),
                        Connective.AND,
                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_SHOOT, t)))));

        // Rule about wumpus (dead or alive)
        tell(new ComplexSentence(
                newSymbol(WUMPUS_ALIVE, t + 1),
                Connective.BICONDITIONAL,
                new ComplexSentence(
                        newSymbol(WUMPUS_ALIVE, t),
                        Connective.AND,
                        new ComplexSentence(Connective.NOT, newSymbol(PERCEPT_SCREAM, t + 1)))));


        if (!disableNavSentences)
            tellSuccessorStateAxioms(t);
    }

    private void tellSuccessorStateAxioms(int t) {
        // Successor state axioms (dependent on location)
        // Rules about current location
        for (int x = 1; x <= caveXDimension; x++) {
            for (int y = 1; y <= caveYDimension; y++) {

                // Location
                List<Sentence> locDisjuncts = new ArrayList<>();
                locDisjuncts.add(new ComplexSentence(
                        newSymbol(LOCATION, t, x, y),
                        Connective.AND,
                        new ComplexSentence(
                                new ComplexSentence(Connective.NOT, newSymbol(ACTION_FORWARD, t)),
                                Connective.OR,
                                newSymbol(PERCEPT_BUMP, t + 1))));
                if (x > 1) { // West room is possible
                    locDisjuncts.add(new ComplexSentence(
                            newSymbol(LOCATION, t, x - 1, y),
                            Connective.AND,
                            new ComplexSentence(
                                    newSymbol(FACING_EAST, t),
                                    Connective.AND,
                                    newSymbol(ACTION_FORWARD, t))));
                }
                if (y < caveYDimension) { // North room is possible
                    locDisjuncts.add(new ComplexSentence(
                            newSymbol(LOCATION, t, x, y + 1),
                            Connective.AND,
                            new ComplexSentence(
                                    newSymbol(FACING_SOUTH, t),
                                    Connective.AND,
                                    newSymbol(ACTION_FORWARD, t))));
                }
                if (x < caveXDimension) { // East room is possible
                    locDisjuncts.add(new ComplexSentence(
                            newSymbol(LOCATION, t, x + 1, y),
                            Connective.AND,
                            new ComplexSentence(
                                    newSymbol(FACING_WEST, t),
                                    Connective.AND,
                                    newSymbol(ACTION_FORWARD, t))));
                }
                if (y > 1) { // South room is possible
                    locDisjuncts.add(new ComplexSentence(
                            newSymbol(LOCATION, t, x, y - 1),
                            Connective.AND,
                            new ComplexSentence(
                                    newSymbol(FACING_NORTH, t),
                                    Connective.AND,
                                    newSymbol(ACTION_FORWARD, t))));
                }

                tell(new ComplexSentence(
                        newSymbol(LOCATION, t + 1, x, y),
                        Connective.BICONDITIONAL,
                        Sentence.newDisjunction(locDisjuncts)));

                // Optimization to make questions about unvisited locations faster
                tell(new ComplexSentence(
                        newSymbol(LOCATION, t + 1, x, y),
                        Connective.IMPLICATION,
                        newSymbol(LOCATION_VISITED, x, y)));
            }
        }

        //
        // Successor state axioms (independent of location)
        // Rules about current orientation
        // Facing North
        tell(new ComplexSentence(
                newSymbol(FACING_NORTH, t + 1),
                Connective.BICONDITIONAL,
                Sentence.newDisjunction(
                        new ComplexSentence(newSymbol(FACING_WEST, t), Connective.AND, newSymbol(ACTION_TURN_RIGHT, t)),
                        new ComplexSentence(newSymbol(FACING_EAST, t), Connective.AND, newSymbol(ACTION_TURN_LEFT, t)),
                        new ComplexSentence(newSymbol(FACING_NORTH, t),
                                Connective.AND,
                                new ComplexSentence(
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_LEFT, t)),
                                        Connective.AND,
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_RIGHT, t))))
                )));
        // Facing South
        tell(new ComplexSentence(
                newSymbol(FACING_SOUTH, t + 1),
                Connective.BICONDITIONAL,
                Sentence.newDisjunction(
                        new ComplexSentence(newSymbol(FACING_WEST, t), Connective.AND, newSymbol(ACTION_TURN_LEFT, t)),
                        new ComplexSentence(newSymbol(FACING_EAST, t), Connective.AND, newSymbol(ACTION_TURN_RIGHT, t)),
                        new ComplexSentence(newSymbol(FACING_SOUTH, t),
                                Connective.AND,
                                new ComplexSentence(
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_LEFT, t)),
                                        Connective.AND,
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_RIGHT, t))))
                )));
        // Facing East
        tell(new ComplexSentence(
                newSymbol(FACING_EAST, t + 1),
                Connective.BICONDITIONAL,
                Sentence.newDisjunction(
                        new ComplexSentence(newSymbol(FACING_NORTH, t), Connective.AND, newSymbol(ACTION_TURN_RIGHT, t)),
                        new ComplexSentence(newSymbol(FACING_SOUTH, t), Connective.AND, newSymbol(ACTION_TURN_LEFT, t)),
                        new ComplexSentence(newSymbol(FACING_EAST, t),
                                Connective.AND,
                                new ComplexSentence(
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_LEFT, t)),
                                        Connective.AND,
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_RIGHT, t))))
                )));
        // Facing West
        tell(new ComplexSentence(
                newSymbol(FACING_WEST, t + 1),
                Connective.BICONDITIONAL,
                Sentence.newDisjunction(
                        new ComplexSentence(newSymbol(FACING_NORTH, t), Connective.AND, newSymbol(ACTION_TURN_LEFT, t)),
                        new ComplexSentence(newSymbol(FACING_SOUTH, t), Connective.AND, newSymbol(ACTION_TURN_RIGHT, t)),
                        new ComplexSentence(newSymbol(FACING_WEST, t),
                                Connective.AND,
                                new ComplexSentence(
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_LEFT, t)),
                                        Connective.AND,
                                        new ComplexSentence(Connective.NOT, newSymbol(ACTION_TURN_RIGHT, t))))
                )));
    }

    @Override
    public String toString() {
        List<Sentence> sentences = getSentences();
        if (sentences.size() == 0) {
            return "";
        } else {
            boolean first = true;
            StringBuilder sb = new StringBuilder();
            for (Sentence s : sentences) {
                if (!first) {
                    sb.append("\n");
                }
                sb.append(s.toString());
                first = false;
            }
            return sb.toString();
        }
    }

    public PropositionSymbol newSymbol(String prefix, int timeStep) {
        return new PropositionSymbol(prefix + "_" + timeStep);
    }

    public PropositionSymbol newSymbol(String prefix, int x, int y) {
        return new PropositionSymbol(prefix + "_" + x + "_" + y);
    }

    public PropositionSymbol newSymbol(String prefix, int timeStep, int x, int y) {
        return newSymbol(newSymbol(prefix, timeStep).toString(), x, y);
    }
}
