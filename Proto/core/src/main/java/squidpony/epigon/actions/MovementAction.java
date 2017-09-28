package squidpony.epigon.actions;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import squidpony.squidgrid.Direction;
import squidpony.squidmath.Coord;

import squidpony.epigon.data.specific.Physical;

/**
 * Represents an action attempting to move the target.
 *
 * @author Eben Howard
 */
public class MovementAction implements Action {

    public Physical mover;
    public boolean forced;
    public Queue<Coord> moveList;//movements in order of execution

    /**
     * Constructor which sets the movement type to default and forced to false.
     */
    public MovementAction(Physical mover, Coord end) {
        this(mover, Collections.singletonList(end), false);
    }

    /**
     * Constructor which sets the end point as provided.
     */
    public MovementAction(Physical mover, Coord end, boolean forced) {
        this(mover, Collections.singletonList(end), forced);
    }

    /**
     * Constructor which sets the point in the given direction as the end point.
     */
    public MovementAction(Physical mover, Direction dir, boolean forced) {
        this(mover, Collections.singletonList(Coord.get(mover.location.x + dir.deltaX, mover.location.y + dir.deltaY)), forced);
    }

    /**
     * Constructor which takes a complete queue of points for the movement path. The starting
     * location should not be in the queue, but the end location must be.
     */
    public MovementAction(Physical mover, List<Coord> moveList, boolean forced) {
        this.mover = mover;
        this.forced = forced;
        this.moveList = new LinkedList<>(moveList);
    }
}