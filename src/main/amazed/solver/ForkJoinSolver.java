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
    private DescendantNode init;

    private Stack<DescendantNode> front;

    public class DescendantNode {
        public int value;
        public int parent;

        public DescendantNode(int value, int parent) {
            this.value = value;
            this.parent = parent;
        }
    }

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
        this.init = new DescendantNode(start, start);
        this.front = new Stack<>();
        // System.out.println("RAN");
    }

    private ForkJoinSolver(Maze maze, int forkAfter, Map<Integer, Integer> predecessor, DescendantNode node) {
        this(maze, forkAfter);
        this.predecessor = predecessor;
        this.steps = 0;
        this.front = new Stack<>();
        this.init = node;
        // System.out.println("New child");

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
        if (!visited.contains(this.init.value)) {
            this.front.push(this.init);
            this.player = maze.newPlayer(this.init.value);

            // System.out.println("start: " + this.init.value);
        } else {
            // System.out.println("Duplicated player!");
            return null;
        }

        while (!this.front.empty() && !finished) {
            Set<DescendantNode> children;
            DescendantNode current;

            try {
                current = front.pop();

                if (this.isGoal(current.value)) {
                    synchronized (predecessor) {
                        predecessor.put(current.value, current.parent);
                    }

                    this.maze.move(this.player, current.value);
                    // System.out.println(this.init.value + " I found the goal. @ " +
                    // current.value);

                    return this.pathFromTo(outset, current.value);
                }

                children = this.explore(current);
            } catch (Exception e) {
                // System.out.println("I have noting left in frontier or my frontier has already
                // been explored. ");

                continue;
            }

            // Add children to frontier
            int childNodeCount = 0;

            for (DescendantNode childNode : children) {
                if (!visited.contains(childNode.value)) {
                    front.push(childNode);
                    childNodeCount++;
                }
            }

            // Fork after
            int mode = 0; // 0 - forkAfter, 1 - branching
            boolean parentShouldWait = false;

            if (mode == 0) {
                if (this.steps % (this.forkAfter + 1) == 0) {
                    try {
                        this.createTask(front.pop(), parentShouldWait);
                    } catch (Exception e) {
                        // System.out.println("Too slow empty stack, or reached a dead end.");
                        return null;
                    }

                }
            }

            // Fork on each branch
            if (mode == 1 && childNodeCount > 1) {
                this.createTask(front.pop(), parentShouldWait);
            }
        }

        return joinTasks();
    }

    private boolean isGoal(int current) {
        if (this.maze.hasGoal(current)) {
            // maze.move(player, current);

            ForkJoinSolver.finished = true;
        }

        return ForkJoinSolver.finished;
    }

    private List<Integer> joinTasks() {
        ArrayList<String> w = new ArrayList<>();

        this.subtasks.forEach((x) -> {
            w.add(Integer.toString(x.start));
        });

        // System.out.println(this.start + " waiting for: " + w.toString());

        for (ForkJoinSolver st : subtasks) {
            List<Integer> sp = st.join();

            if (sp != null) {
                // System.out.println("here: " + sp);
                return sp;
            }
        }

        return null;
    }

    private void createTask(DescendantNode fromNode, boolean wait) {
        // System.out.println("Creating task");
        ForkJoinSolver task = new ForkJoinSolver(maze, this.forkAfter, this.predecessor, fromNode);

        this.subtasks.add(task);

        task.fork();

        if (wait) {
            task.join();
        }
    }

    private Set<DescendantNode> explore(DescendantNode current) throws Exception {
        synchronized (visited) {
            if (!visited.contains(current.value)) {
                visited.add(current.value);
                predecessor.put(current.value, current.parent);
            } else {
                throw new Exception("Node is already visited");
            }
        }

        maze.move(player, current.value);
        steps++;

        Set<DescendantNode> children = new HashSet<>();

        for (int child : maze.neighbors(current.value)) {
            children.add(new DescendantNode(child, current.value));
        }

        return children;
    }

}
