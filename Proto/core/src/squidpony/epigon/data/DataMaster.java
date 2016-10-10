package squidpony.epigon.data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import squidpony.epigon.exception.WarningObserver;

/**
 * Singleton class which holds all of the blueprints and created items.
 *
 * This class does not include the Stats as they are not gathered at run time.
 *
 * @author Eben Howard -- http://squidpony.com
 */
@Deprecated
public class DataMaster {

    private class DataNode {
        EpiData data;
        HashMap<EdgeType, ArrayList<DataNode>> edges = new HashMap<>();

        DataNode(EpiData data) {
            this.data = data;
            for (EdgeType et : EdgeType.values()) {
                edges.put(et, new ArrayList<DataNode>());
            }
        }
    }

    private enum EdgeType {

        PARENT, CHILD, CONTAINS, CONTAINEDBY, INSTANTIATION;
    }
    private EnumMap<DataType, ArrayList<EpiData>> mapping = new EnumMap<>(DataType.class);
    private static WarningObserver warningObserver = WarningObserver.getInstance();
    private HashMap<String, EpiData> index = new HashMap<>();
    private LinkedList<EpiData> stubs = new LinkedList<>();//objects which should have links but they have not yet been fully resolved

    public DataMaster() {
        for (DataType dt : DataType.values()) {
            mapping.put(dt, new ArrayList<EpiData>());
        }
    }

    public EnumMap<DataType, ArrayList<EpiData>> getMapping() {
        return mapping;
    }

    public void setMapping(EnumMap<DataType, ArrayList<EpiData>> mapping) {
        this.mapping = mapping;
    }

    /**
     * Returns the backing list used for the passed in data type. This list is
     * untyped so returned objects will need to be cast to the appropriate type
     * before working with them.
     *
     * @param type
     * @return
     */
    public synchronized ArrayList<EpiData> getList(DataType type) {
        return mapping.get(type);
    }

    /**
     * Adds the given object to the appropriate list.
     *
     * If the the object was already in the list (by matching Internal Name),
     * then that object is updated.
     *
     * @param ed
     * @param type
     */
    public synchronized void add(EpiData ed, DataType type) {
        try {
            int i = mapping.get(type).indexOf(ed);
            if (i >= 0) {
                EpiData old = mapping.get(type).remove(i);
                //old.copyFrom(ed);
                ed = old;
            }
            mapping.get(type).add(ed);
        } catch (ClassCastException ex) {
            System.err.println(ex);
        }
    }

    /**
     * Removes the given object if it's in the data store.
     *
     * @param ed
     * @param type
     */
    public synchronized void remove(EpiData ed, DataType type) {
        //move parent pointers accordingly
        if (mapping.get(type).remove(ed)) {
            for (EpiData data : mapping.get(type)) {
//                if (data.hasParent(null)) {
//                    if (data.getParent().equals(ed)) {//make sure to only change direct children
//                        data.setParent(ed.getParent());
//                    }
//                }
            }
        }
    }

    public synchronized void remove(String name, DataType type) {
        EpiData removed = null;
        for (EpiData ed : mapping.get(type)) {
//            if (ed.getInternalName() == null ? name == null : ed.getInternalName().equals(name)) {
//                removed = ed;
//                break;
//            }
        }
        if (removed != null) {
            remove(removed, type);
        }
    }

    /**
     * Adds the entire provided list to the data store.
     *
     * @param list
     * @param type
     */
    public synchronized void addAll(ArrayList<? extends EpiData> list, DataType type) {
        for (EpiData ed : list) {
            add(ed, type);
        }
    }

    /**
     * Adds all of the data in the passed in data master to this object's
     * backing structure.
     *
     * @param other
     */
    public synchronized void addAll(DataMaster other) {
        for (DataType dt : DataType.values()) {
            addAll(other.getList(dt), dt);
        }
    }
}
