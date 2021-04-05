package squidpony.epigon.input.mouse;

import com.badlogic.gdx.Input;

import java.util.List;

import squidpony.squidmath.Coord;
import squidpony.squidmath.StatefulRNG;

import squidpony.epigon.Epigon;
import squidpony.epigon.data.Physical;
import squidpony.epigon.data.Weapon;

/**
 * Handles mouse input for the main map screen
 */
public class MapMouseHandler extends EpigonMouseHandler {

    private Epigon epigon;

    @Override
    public EpigonMouseHandler setEpigon(Epigon epigon) {
        this.epigon = epigon;
        return this;
    }

    // if the user clicks within FOV range and there are no awaitedMoves queued up, generate toCursor if it
    // hasn't been generated already by mouseMoved, then copy it over to awaitedMoves.
    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        screenX += epigon.player.location.x - (epigon.mapSize.gridWidth >> 1);
        screenY += epigon.player.location.y - (epigon.mapSize.gridHeight >> 1);

        if (!epigon.map.inBounds(screenX, screenY) || (!epigon.showingMenu && epigon.map.lighting.fovResult[screenX][screenY] <= 0.0)) {
            return false;
        }

        Physical thing = null;
        if (epigon.map.contents[screenX][screenY] != null) {
            thing = epigon.map.contents[screenX][screenY].getCreature();
        }
        switch (button) {
            case Input.Buttons.LEFT:
                if (epigon.showingMenu) {
                    boolean menuInBounds = epigon.menuLocation.x <= screenX && epigon.menuLocation.y <= screenY && screenY - epigon.menuLocation.y < epigon.maneuverOptions.size();
                    if (menuInBounds && epigon.currentTarget != null && epigon.mapHoverSLayers.backgrounds[screenX << 1][screenY] != 0f) {
                        epigon.attack(epigon.currentTarget, epigon.maneuverOptions.getAt(screenY - epigon.menuLocation.y));
                        epigon.calcFOV(epigon.player.location.x, epigon.player.location.y);
                        epigon.calcDijkstra();
                        epigon.runTurn();
                    }
                    epigon.showingMenu = false;
                    epigon.menuLocation = null;
                    epigon.maneuverOptions.clear();
                    epigon.interactionOptions.clear();
                    epigon.currentTarget = null;
                    epigon.mapHoverSLayers.clear();
                    return true;
                }

                if (epigon.cursor.x != screenX || epigon.cursor.y != screenY) {// clear cursor if lifted in space other than the one it went down in
                    epigon.toCursor.clear();
                    return false;//cleaned up but not considered "handled"
                }

                if (thing == null) {
                    if (epigon.toCursor.isEmpty()) {
                        epigon.cursor = Coord.get(screenX, screenY);
                        ((StatefulRNG) epigon.toPlayerDijkstra.rng).setState(epigon.player.location.hashCode() ^ (long) epigon.cursor.hashCode() << 32);
                        epigon.toPlayerDijkstra.findPathPreScanned(epigon.toCursor, epigon.cursor);
                        if (!epigon.toCursor.isEmpty()) {
                            epigon.toCursor.remove(0); // Remove cell you're in from list
                        }
                    }
                    epigon.awaitedMoves.addAll(epigon.toCursor);
                    return true;
                } else {
                    List<Weapon> attackOptions = epigon.validAttackOptions(epigon.player, thing);
                    if (attackOptions == null || attackOptions.isEmpty()) {
                        epigon.message("Can't attack the " + thing.name + " from there.");
                    } else {
                        Weapon w = epigon.rng.getRandomElement(attackOptions);
                        epigon.attack(thing, w);
                        epigon.calcFOV(epigon.player.location.x, epigon.player.location.y);
                        epigon.calcDijkstra();
                        epigon.runTurn();
                    }
                }

                return true;
            case Input.Buttons.RIGHT:
                if (thing == null) {
                    return false;
                }
                epigon.buildAttackOptions(thing);
                if (epigon.maneuverOptions == null || epigon.maneuverOptions.isEmpty()) {
                    epigon.message("No attack options against the " + thing.name + " at this range.");
                } else {
                    epigon.menuLocation = epigon.showAttackOptions(thing, epigon.maneuverOptions);
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return mouseMoved(screenX, screenY);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!epigon.awaitedMoves.isEmpty()) {
            epigon.cancelMove();
            return true;
        }

        switch (button) {
            case Input.Buttons.LEFT:
                return false;
            case Input.Buttons.RIGHT:
                // TODO - add tooltip info for location
                break;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        screenX += epigon.player.location.x - (epigon.mapSize.gridWidth >> 1);
        screenY += epigon.player.location.y - (epigon.mapSize.gridHeight >> 1);
        if (!epigon.map.inBounds(screenX, screenY) || epigon.map.lighting.fovResult[screenX][screenY] <= 0.0) {
            epigon.toCursor.clear();
            return false;
        }
        epigon.contextHandler.tileContents(screenX, screenY, epigon.depth, epigon.map.contents[screenX][screenY]); // TODO - have ground level read as depth 0
        epigon.infoHandler.setTarget(epigon.map.contents[screenX][screenY].getCreature());

        if (!epigon.awaitedMoves.isEmpty()) {
            return false;
        }

        epigon.cursor = Coord.get(screenX, screenY);
        epigon.toCursor.clear();
        ((StatefulRNG) epigon.toPlayerDijkstra.rng).setState(epigon.player.location.hashCode() ^ (long) epigon.cursor.hashCode() << 32);
        epigon.toPlayerDijkstra.findPathPreScanned(epigon.toCursor, epigon.cursor);
        if (!epigon.toCursor.isEmpty()) {
            epigon.toCursor.remove(0);
        }
        return false;
    }
}
