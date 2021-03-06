package com.limpygnome.parrot.library.crypto;

import java.util.UUID;

/**
 * Immutable generic encrypted value.
 *
 * Use {@link CryptoReaderWriter} for interaction.
 */
public abstract class EncryptedValue
{
    // Unique identifier for this value
    private UUID id;

    // The epoch time at which this value was last modified
    private long lastModified;

    /**
     * Sets generic data.
     *
     * @param id unique identifier; can be null for randomly generated value to be assigned
     * @param lastModified the epoch time at which this value was last modified
     */
    protected EncryptedValue(UUID id, long lastModified)
    {
        this.id = id != null ? id : UUID.randomUUID();
        this.lastModified = lastModified;
    }

    /**
     * @return unique identifier for this value
     */
    public UUID getUuid()
    {
        return id;
    }

    /**
     * @return unique identifier for this value
     */
    public String getId()
    {
        return id != null ? id.toString() : null;
    }


    /**
     * @return epoch time of when this value was last modified
     */
    public long getLastModified()
    {
        return lastModified;
    }

    /**
     * Creates a cloned instance, whereby identifier is also inherited.
     *
     * @return cloned instance
     */
    public abstract EncryptedValue clone();

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EncryptedValue that = (EncryptedValue) o;

        return lastModified == that.lastModified;

    }

    @Override
    public int hashCode()
    {
        return (int) (lastModified ^ (lastModified >>> 32));
    }

}
