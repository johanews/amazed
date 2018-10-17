package amazed.solver;

import amazed.maze.Maze;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <code>ForkJoinSolver</code> implements a solver for <code>Maze</code> objects
 * using a fork/join multi-thread depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver extends SequentialSolver {

    private static ConcurrentSkipListSet<Integer> visited = new ConcurrentSkipListSet<>();
    private static boolean finished = false;

    private List<ForkJoinSolver> subtasks = new ArrayList<>();
    private int outset = start;
    private int player;
    private int steps;

    /**
     * Creates a solver that searches in <code>maze</code> from the start node to a
     * goal.
     *
     * @param maze the maze to be searched
     */
    public ForkJoinSolver(Maze maze) {
        super(maze);
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the start node to a
     * goal, forking after a given number of visited nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after which a parallel
     *                  task is forked; if <code>forkAfter &lt;= 0</code> the solver
     *                  never forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    private ForkJoinSolver(Maze maze, int forkAfter, Map<Integer, Integer> predecessor, int node) {
        this(maze, forkAfter);
        this.predecessor = predecessor;
        this.steps = 0;
        this.start = node;

        // A node can exist in other frontiers, we want to make sure that only one
        // player can be created on a single node.
    }

    /**
     * Searches for and returns the path, as a list of node identifiers, that goes
     * from the start node to a goal node in the maze. If such a path cannot be
     * found (because there are no goals, or all goals are unreachable), the method
     * returns <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a goal node in
     *         the maze; <code>null</code> if such a path cannot be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        // Prevent double spawns...
        // if (!visited.contains(start)) {
        frontier.push(start);
        this.player = maze.newPlayer(start);
        System.out.println("starting at: " + this.start);
        // }

        while (!this.frontier.empty() && !finished) {
            Set<Integer> children;
            int current;

            try {
                current = frontier.pop();

                if (this.isGoal(current)) {
                    return this.pathFromTo(outset, current);
                }

                children = this.explore(current);
            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println("I have noting left in frontier or my frontier has already been explored. ");

                break;
            }

            this.steps++;

            // Either add children to frontier or fork.

            // Add children to frontier
            int childNodeCount = 0;

            for (Integer childNode : children) {
                frontier.push(childNode);
                childNodeCount++;

                if (!visited.contains(childNode)) {
                    // OBS: not sure of this logic...
                    predecessor.put(childNode, current);
                }
            }

            // Fork after
            int mode = 1; // 0 - forkAfter, 1 - branching
            boolean parentShouldWait = false;

            if (mode == 0) {
                if (this.steps % this.forkAfter == 0) {
                    try {
                        int nextStart = frontier.pop();

                        this.createTask(nextStart, parentShouldWait);
                    } catch (Exception e) {
                        System.out.println("to slow empty stack, or reached a dead end.");
                    }

                }
            }

            // Fork on each branch
            if (mode == 1 && childNodeCount > 1) {
                this.createTask(frontier.pop(), parentShouldWait);
            }
        }

        return joinTasks();
    }

    private boolean isGoal(int current) {
        if (this.maze.hasGoal(current)) {
            maze.move(player, current);

            ForkJoinSolver.finished = true;
        }

        return ForkJoinSolver.finished;
    }

    private List<Integer> joinTasks() {
        for (ForkJoinSolver st : subtasks) {
            List<Integer> sp = st.join();

            if (sp != null) {
                System.out.println("Completed: " + sp);

                return sp;
            }
        }

        return null;
    }

    private void createTask(int fromNode, boolean wait) {
        System.out.println("FROM " + fromNode);
        ForkJoinSolver task = new ForkJoinSolver(maze, this.forkAfter, this.predecessor, fromNode);

        this.subtasks.add(task);

        task.fork();

        if (wait) {
            task.join();
        }
    }

    private Set<Integer> explore(int current) throws Exception {
        synchronized (visited) {
            if (!visited.contains(current)) {
                visited.add(current);
            } else {
                throw new Exception("Node is already visited");
            }

        }

        maze.move(player, current);

        Set<Integer> children = new HashSet<>();

        for (int nb : maze.neighbors(current)) {
            if (!visited.contains(nb)) {
                children.add(nb);
                // visited.add(nb);
            }
        }

        return children;
    }

}
