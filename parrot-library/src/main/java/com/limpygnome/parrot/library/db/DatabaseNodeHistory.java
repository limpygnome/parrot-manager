package com.limpygnome.parrot.library.db;

import com.limpygnome.parrot.library.crypto.EncryptedValue;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Manages the collection of historic values for a node.
 */
public class DatabaseNodeHistory
{
    // The node to which this collection belongs
    private DatabaseNode currentNode;

    // Cached array of historic values retrieved; same reason as children being cached
    EncryptedValue[] historyCached;

    // Previous values stored at this node
    private List<EncryptedValue> history;

    DatabaseNodeHistory(DatabaseNode currentNode)
    {
        this.currentNode = currentNode;
        this.history = new LinkedList<>();
    }

    /**
     * @param encryptedValue value to be added
     */
    public synchronized void add(EncryptedValue encryptedValue)
    {
        history.add(encryptedValue);
        setDirty();
    }

    /**
     * @param values adds collection of values
     */
    public synchronized void addAll(Collection<? extends EncryptedValue> values)
    {
        history.addAll(values);
        setDirty();
    }

    /**
     * @return cached history; result is safe against garbage collection
     */
    public synchronized EncryptedValue[] fetch()
    {
        historyCached = history.toArray(new EncryptedValue[history.size()]);
        return historyCached;
    }

    /**
     * Removes specified value from history.
     *
     * @param encryptedValue value to be removed
     */
    public void remove(EncryptedValue encryptedValue)
    {
        history.remove(encryptedValue);
        setDirty();
    }

    /**
     * clears all stored history.
     */
    public void clearAll()
    {
        history.clear();
        setDirty();
    }

    /**
     * @return total number of historic values
     */
    public int size()
    {
        return history.size();
    }

    public void cloneToNode(DatabaseNode targetNode)
    {
        // Build cloned instance
        DatabaseNodeHistory result = new DatabaseNodeHistory(currentNode);

        for (EncryptedValue encryptedValue : history)
        {
            result.add(encryptedValue.clone());
        }

        // Update target node with cloned history
        targetNode.setHistory(result);
    }

    private void setDirty()
    {
        // Mark database as dirty
        currentNode.getDatabase().setDirty(true);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DatabaseNodeHistory that = (DatabaseNodeHistory) o;

        return history != null ? history.equals(that.history) : that.history == null;

    }

    @Override
    public int hashCode()
    {
        return history != null ? history.hashCode() : 0;
    }

}