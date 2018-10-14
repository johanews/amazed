package amazed.solver;

import amazed.maze.Maze;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver extends SequentialSolver {
    private static final long serialVersionUID = 1L;

    private static Set<Integer> visited = new ConcurrentSkipListSet<>();
    private static Map<Integer, Integer> predecessor = new ConcurrentHashMap<>();
    private static boolean finished = false;

    private List<ForkJoinSolver> subtasks = new ArrayList<>();
    private int outset = start;

    public ForkJoinSolver(Maze maze, int forkAfter, int outset) {
        this(maze, forkAfter);
        this.outset = outset;
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
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze      the maze to be searched
     * @param forkAfter the number of steps (visited nodes) after
     *                  which a parallel task is forked; if
     *                  <code>forkAfter &lt;= 0</code> the solver never
     *                  forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter) {
        this(maze);
        this.forkAfter = forkAfter;
    }

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreachable), the method returns
     * <code>null</code>.
     *
     * @return the list of node identifiers from the start node to a
     * goal node in the maze; <code>null</code> if such a path cannot
     * be found.
     */
    @Override
    public List<Integer> compute() {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {
        int player = maze.newPlayer(outset);
        frontier.push(outset);

        while (!frontier.empty() && !finished) {
            int current = frontier.pop();

            if (maze.hasGoal(current)) {
                maze.move(player, current);
                super.predecessor = predecessor;
                finished = true;
                return pathFromTo(start, current);
            }

            maze.move(player, current);
            visited.add(current);

            Iterator<Integer> it = unvisited(current).iterator();

            if (it.hasNext())
                progress(current, it.next());
            if (it.hasNext()) {
                createTasks(current, it);
            }
        }
        return join_tasks();
    }

    private List<Integer> join_tasks() {
        for (ForkJoinSolver st: subtasks) {
            List<Integer> sp = st.join();
            if (sp != null)
                return sp;
        }
        return null;
    }

    private void createTasks(int current, Iterator<Integer> it) {
        ForkJoinSolver task;
        while (it.hasNext()) {
            int node = it.next();
            task = new ForkJoinSolver(maze, forkAfter, node);
            subtasks.add(task);
            predecessor.put(node, current);
            task.fork();
        }
    }

    private void progress(int current, int next) {
        frontier.push(next);
        predecessor.put(next, current);
    }

    private Set<Integer> unvisited(int current) {
        Set<Integer> result = new HashSet<>();
        for (int nb : maze.neighbors(current)) {
            if (!visited.contains(nb))
                result.add(nb);
        }
        return result;
    }

}
