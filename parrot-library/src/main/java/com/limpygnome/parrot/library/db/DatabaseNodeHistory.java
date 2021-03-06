package com.limpygnome.parrot.library.db;

import com.limpygnome.parrot.library.crypto.EncryptedValue;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private Map<UUID, EncryptedValue> history;

    // The hashcode's of deleted values (used for syncing)
    private Set<UUID> deleted;

    DatabaseNodeHistory(DatabaseNode currentNode)
    {
        this.currentNode = currentNode;
        this.history = new HashMap<>();
        this.deleted = new HashSet<>();
        this.historyCached = new EncryptedValue[0];
    }

    /**
     * Adds the identifier of a deleted item.
     *
     * @param id ID from a deleted {@link EncryptedValue}
     */
    public synchronized void addDeleted(UUID id)
    {
        deleted.add(id);
    }

    /**
     * An array of identifiers of deleted values.
     *
     * @return an array, or empty
     */
    public synchronized UUID[] getDeleted()
    {
        return deleted.toArray(new UUID[deleted.size()]);
    }

    /**
     * @param encryptedValue value to be added
     */
    public synchronized void add(EncryptedValue encryptedValue)
    {
        history.put(encryptedValue.getUuid(), encryptedValue);
        setDirty();
    }

    /**
     * @param values adds collection of values
     */
    public synchronized void addAll(Collection<? extends EncryptedValue> values)
    {
        // Add each value
        values.stream().forEach(encryptedValue -> history.put(encryptedValue.getUuid(), encryptedValue));
        setDirty();
    }

    /**
     * @return cached history; result is safe against garbage collection
     */
    public synchronized EncryptedValue[] fetch()
    {
        return historyCached;
    }

    /**
     * TODO test
     *
     * @param id the id
     * @return retrieves item by id (string uuid)
     */
    public synchronized EncryptedValue fetchById(String id)
    {
        EncryptedValue result = null;

        if (id != null)
        {
            result = history.get(UUID.fromString(id));
        }

        return result;
    }

    /**
     * Removes specified value from history.
     *
     * @param id value to be removed
     */
    public synchronized void delete(String id)
    {
        UUID uuid = UUID.fromString(id);

        if (history.remove(uuid) != null)
        {
            // Add to list of values deleted
            deleted.add(uuid);
        }

        setDirty();
    }

    /**
     * Clears all stored history.
     *
     * TODO update unit test
     */
    public synchronized void deleteAll()
    {
        // Add all current items as deleted
        deleted.addAll(history.keySet());

        // Clear history
        history.clear();

        setDirty();
    }

    /**
     * Restores a historic value as the current value.
     *
     * TODO unit test
     *
     * @param id encrypted value identifier
     */
    public synchronized void restore(String id)
    {
        EncryptedValue encryptedValue = fetchById(id);

        if (encryptedValue != null)
        {
            this.currentNode.setValue(encryptedValue);
        }
    }

    /**
     * @return total number of historic values
     */
    public synchronized int size()
    {
        return history.size();
    }

    /**
     * Merges items from another history into this history.
     *
     * @param otherHistory the other history
     */
    public synchronized void merge(DatabaseNodeHistory otherHistory)
    {
        boolean isDirty = false;

        // Add any missing values, unless deleted
        EncryptedValue clone;

        for (EncryptedValue encryptedValue : otherHistory.history.values())
        {
            if (!deleted.contains(encryptedValue.getUuid()) && !this.history.containsKey(encryptedValue.getUuid()))
            {
                clone = encryptedValue.clone();
                this.history.put(clone.getUuid(), clone);
                isDirty = true;
            }
        }

        // Add any missing deleted values
        if (deleted.addAll(otherHistory.deleted))
        {
            isDirty = true;
        }

        // Mark database as dirty
        if (isDirty)
        {
            setDirty();
        }
    }

    private void setDirty()
    {
        // Mark database as dirty
        currentNode.getDatabase().setDirty(true);

        // Refresh items
        refreshItemCache();
    }

    private void refreshItemCache()
    {
        historyCached = history.values().toArray(new EncryptedValue[history.size()]);
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
