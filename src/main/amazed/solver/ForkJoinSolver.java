package amazed.solver;

import amazed.maze.Maze;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by
 * a <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver extends SequentialSolver {

    private static Set<Integer> visited = new ConcurrentSkipListSet<>();
    private static boolean finished = false;

    private List<ForkJoinSolver> subtasks = new ArrayList<>();
    private int player;
    private int steps;
    private DescendantNode init;
    private Stack<DescendantNode> front;

    public class DescendantNode {
        int value;
        int parent;

        DescendantNode(int value, int parent) {
            this.value = value;
            this.parent = parent;
        }
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze the maze to be searched
     */
    private ForkJoinSolver(Maze maze) {
        super(maze);
        this.init = new DescendantNode(start, start);
        this.front = new Stack<>();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze          the maze to be searched
     * @param forkAfter     the number of steps after which a parallel
     *                      task is forked (if 0, the solver will not
     *                      fork new tasks)
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * outset node to a goal.
     *
     * @param maze          the maze to be searched
     * @param forkAfter     the number of steps after which a parallel
     *                      task is forked (if 0, the solver will not
     *                      fork new tasks)
     * @param predecessor   the parent's predecessor map so that
     *                      each child can continue the mapping
     * @param outset        the starting point for the solver
     */
    private ForkJoinSolver(Maze maze, int forkAfter, Map<Integer, Integer> predecessor, DescendantNode outset) {
        this(maze, forkAfter);
        this.steps = 0;
        this.init = outset;
        this.predecessor = new HashMap<>(predecessor);
    }

    /**
     * Searches for and returns the path, as a list of node identifiers
     * that goes from the start node to a goal node in the maze. If such
     * a path cannot be found (because there are no goals, or all goals
     * are unreachable), the method returns <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a goal
     * node in the maze; if no such a path exists <code>null</code> is
     * returned
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    /**
     * A hybrid solution that implements different searching logic depending
     * on environment variables and input parameters.
     *
     * @return the path of nodes between the start and the goal node
     * if such exists, otherwise <code>null</code>
     */
    private List<Integer> parallelSearch() {

        // Make sure no other solver has visited this
        // node (if so, return null right away)
        synchronized (visited) {
            if (!visited.contains(init.value)) {
                front.push(this.init);
                player = maze.newPlayer(init.value);
            } else { return null; }
        }

        while (!front.empty() && !finished) {
            Set<DescendantNode> children;
            DescendantNode current;

            try {

                current = front.pop();

                if (maze.hasGoal(current.value)) {
                    finished = true;
                    synchronized (visited) {
                        visited.add(current.value);
                        predecessor.put(current.value, current.parent);
                    }
                    maze.move(player, current.value);
                    return pathFromTo(start, current.value);
                }

                children = explore(current);

            } catch (Exception e) {
                // Either the frontier is empty or
                // the current node already visited
                continue;
            }

            // Add unvisited nodes to the frontier
            int childNodeCount = 0;

            for (DescendantNode childNode : children) {
                if (!visited.contains(childNode.value)) {
                    front.push(childNode);
                    childNodeCount++;
                }
            }

            /*
             * Collect the current environment configurations. The MODE determines
             * the forking logic and could be either 0 or 1.
             *
             *  0:  Continue sequentially for the number of consecutive steps
             *      defined by forkAfter before forking new threads.
             *
             *  1:  Fork new threads only at intersections allowing different
             *      threads to explore the different routes.
             *
             * The SHOULD_WAIT variable is of type boolean and informs the solver
             * to either wait for its spawned children or continue in parallel.
             *
             */
            int mode = Integer.parseInt(System.getenv("MODE"));
            boolean parentShouldWait = Boolean.parseBoolean(System.getenv("SHOULD_WAIT"));

            if (mode == 0) {

                if (steps % (forkAfter + 1) == 0) {

                    try {

                        Iterator<DescendantNode> it = front.iterator();
                        if (it.hasNext())
                            it.next();
                        while (it.hasNext())
                            createTask(it.next());

                        if (parentShouldWait)
                            joinTasks();

                    } catch (Exception e) {
                        return null;
                    }
                }
            }

            if (mode == 1 && childNodeCount > 1) {
                int size = front.size();
                while (front.size() > (size - childNodeCount + 1))
                    createTask(front.pop());
            }
        }

        return joinTasks();
    }

    /**
     * Before returning, the parent thread waits for its subtasks to
     * return their individual results. Once a subtask returns a list
     * of integers, the goal is reached and the result is propagated
     * up the tree of tasks until it reaches the root.
     *
     * @return the path of nodes between the start and the goal node
     * if such exists, otherwise <code>null</code>
     */
    private List<Integer> joinTasks() {
        for (ForkJoinSolver st: subtasks) {
            List<Integer> sp = st.join();
            if (sp != null)
                return sp;
        }
        return null;
    }

    /**
     * Each node is assigned a new solver that is added to the list
     * of subtasks before start.
     *
     * @param node the current node
     */
    private void createTask(DescendantNode node) {
        ForkJoinSolver task = new ForkJoinSolver(maze, forkAfter, predecessor, node);
        subtasks.add(task);
        task.fork();
    }

    /**
     * Explores the opportunities of subsequent progression. It
     * uses the {@link amazed.maze.Maze#neighbors(int) neighbors}
     * method to identify valid adjacent nodes before filtering
     * out the once that have not yet been visited.
     *
     * @param current the current node
     * @return the <code>Set</code> of unvisited nodes reachable
     * from the current position
     */
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
