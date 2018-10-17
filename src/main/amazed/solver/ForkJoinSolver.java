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
        frontier.push(start);
        this.player = maze.newPlayer(start);
        System.out.println("starting at: " + this.start);

        while (!this.frontier.empty() && !finished) {
            int current;

            current = frontier.pop();

            if (this.isGoal(current)) {
                return this.pathFromTo(start, current);
            }

            Set<Integer> children = this.explore(current);

            // Either add children to frontier or fork.

            // Add children to frontier
            for (Integer childNode : children) {
                frontier.push(childNode);

                if (!visited.contains(childNode)) {
                    // OBS: not sure of this logic...
                    predecessor.put(childNode, current);
                }
            }

            this.steps++;

            System.out.println(this.steps + " " + this.forkAfter);

            if (this.steps % this.forkAfter == 0) {
                int nextStart = frontier.pop();

                this.createTask(nextStart);
            }
            // Fork after
            // ...

            // Fork on each split
            // ...

            // if (children.hasNext()) {
            // // Allow "parrent" to continue to do work.
            // progress(current, children.next());
            // }

            // if (children.hasNext()) {
            // // Pas the itterator down and spawn new searches for each split.
            // createTasks(current, children);
            // }
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
                System.out.println(sp);

                return sp;
            }
        }

        return null;
    }

    private void createTask(int fromNode) {
        System.out.println("FROM " + fromNode);
        ForkJoinSolver task = new ForkJoinSolver(maze, this.forkAfter, this.predecessor, fromNode);

        this.subtasks.add(task);

        task.fork();

        return;
    }

    private void createTasks(int current, Iterator<Integer> children) {
        while (children.hasNext()) {
            int node;
            ForkJoinSolver task = null;

            synchronized (visited) {
                node = children.next();

                if (!visited.contains(node)) {
                    task = new ForkJoinSolver(maze, forkAfter, predecessor, node);
                    visited.add(node);
                }
            }

            if (task == null) {
                return;
            }

            subtasks.add(task);
            predecessor.put(node, current); // Allow predecessor to keep track of "child searches".

            task.fork();
            // task.join(); // only for testing
        }
    }

    private void progress(int current, int next) {
        frontier.push(next);
        visited.add(next);
        predecessor.put(next, current);
    }

    private Set<Integer> explore(int current) {
        visited.add(current);
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
