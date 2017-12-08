package squidpony.epigon.input;

import squidpony.Maker;
import squidpony.squidmath.OrderedMap;

import static squidpony.epigon.input.Verb.*;
import static squidpony.squidgrid.gui.gdx.SquidInput.*;

/**
 * The set of keyboard inputs.
 */
public class ControlMapping {

    private static final int CAPS = 0x40000, CTRL = 0x20000;
    public static final OrderedMap<Integer, Verb> defaultMapping =
            Maker.<Integer, Verb>makeOM(
                    (int)'A'|CAPS, ATTEMPT,
                    (int)'c', CONSUME,
                (int)'c'|CTRL, CONSUME_DIFFERENTLY, // ctrl-c to eat the delicious soap
                (int)'e', EQUIPMENT,
                (int)'f', FIRE,
                (int)'G'|CAPS, GET,
                (int)'g', GATHER,
                (int)'?'|CAPS, HELP,
                (int)F1, HELP,
                (int)'i', INTERACT,
                (int)'d', DRAW,
                (int)'r', REST,
                (int)'p', USE_POWER,
                (int)'v', VIEW,
                (int)'x', EXAMINE,
                (int)UP_ARROW, MOVE_UP,
                (int)DOWN_ARROW, MOVE_DOWN,
                (int)LEFT_ARROW, MOVE_LEFT,
                (int)RIGHT_ARROW, MOVE_RIGHT,
                (int)UP_LEFT_ARROW, MOVE_UP_LEFT,
                (int)UP_RIGHT_ARROW, MOVE_UP_RIGHT,
                (int)DOWN_LEFT_ARROW, MOVE_DOWN_LEFT,
                (int)DOWN_RIGHT_ARROW, MOVE_DOWN_RIGHT,
                (int)'h', MOVE_LEFT,
                (int)'j', MOVE_DOWN,
                (int)'k', MOVE_UP,
                (int)'l', MOVE_RIGHT,
                (int)'y', MOVE_UP_LEFT,
                (int)'u', MOVE_UP_RIGHT,
                (int)'b', MOVE_DOWN_LEFT,
                (int)'n', MOVE_DOWN_RIGHT,
                (int)'w', WAIT,
                (int)'.', WAIT,
                (int)'<'|CAPS, MOVE_HIGHER,
                (int)'>'|CAPS, MOVE_LOWER,
                (int)'[', CONTEXT_PRIOR,
                (int)']', CONTEXT_NEXT,
                (int)'{'|CAPS, INFO_PRIOR,
                (int)'}'|CAPS, INFO_NEXT,
                (int)'o', OPEN,
                (int)'s', SHUT,
                (int)'S'|CAPS, SAVE,
                (int)'S'|CTRL, SAVE,
                (int)'Q'|CAPS, QUIT,
                (int)'q'|CTRL, QUIT,
                (int)ESCAPE, QUIT
        );
}
