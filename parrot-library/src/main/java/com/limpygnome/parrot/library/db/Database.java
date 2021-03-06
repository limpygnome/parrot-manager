package com.limpygnome.parrot.library.db;

import com.limpygnome.parrot.library.crypto.*;
import com.limpygnome.parrot.library.event.DatabaseDirtyEventHandler;
import com.limpygnome.parrot.library.event.EventHandlerCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Represents a database for storing confidential details.
 *
 * Simple tree structure with a root db, which breaks down into recursive child nodes.
 *
 * This is also responsible for all cryptography for the database.
 *
 * Thread safe.
 */
public class Database
{
    private static final Logger LOG = LoggerFactory.getLogger(Database.class);

    // Components
    private CryptoReaderWriter cryptoReaderWriter;
    private CryptoParamsFactory cryptoParamsFactory;

    // Indicates if the database has been modified (not persisted)
    private boolean isDirty;

    // Params used for file crypto
    private CryptoParams fileCryptoParams;

    // Params used for memory crypto
    private CryptoParams memoryCryptoParams;

    // The root node of the database
    private DatabaseNode root;

    // Quick ref map of UUID (String) to node; this must be updated in places where nodes are added/removed etc
    private DatabaseLookup lookup;

    // Event handlers
    private EventHandlerCollection<DatabaseDirtyEventHandler> dirtyEventHandlers;
    private boolean eventsEnabled;


    private Database()
    {
        // Enable events
        eventsEnabled = true;

        // Setup handler collections
        dirtyEventHandlers = new EventHandlerCollection<DatabaseDirtyEventHandler>()
        {
            @Override
            protected void invoke(DatabaseDirtyEventHandler instance, Object... args)
            {
                instance.eventDatabaseDirtyEventHandler((Database) args[0], (Boolean) args[1]);
            }
        };

        // Setup an initial blank root node
        lookup = new DatabaseLookup();

        root = new DatabaseNode(this, UUID.randomUUID(), null, 0, (EncryptedAesValue) null);
        lookup.add(root);
    }

    /**
     * Creates a new instance.
     *
     * @param memoryCryptoParams params for in-memory crypto
     * @param fileCryptoParams params used for file crypto; only required for writing to file later, can be null
     */
    public Database(CryptoParams memoryCryptoParams, CryptoParams fileCryptoParams)
    {
        this();

        // Setup components
        this.cryptoReaderWriter = new CryptoReaderWriter();
        this.cryptoParamsFactory = new CryptoParamsFactory();

        // Setup initial state
        this.memoryCryptoParams = memoryCryptoParams;
        this.fileCryptoParams = fileCryptoParams;
    }

    Database(CryptoParams memoryCryptoParams, CryptoParams fileCryptoParams,
                       CryptoReaderWriter cryptoReaderWriter, CryptoParamsFactory cryptoParamsFactory)
    {
        this();

        this.cryptoReaderWriter = cryptoReaderWriter;
        this.cryptoParamsFactory = cryptoParamsFactory;
        this.memoryCryptoParams = memoryCryptoParams;
        this.fileCryptoParams = fileCryptoParams;
    }

    DatabaseLookup getLookup()
    {
        return lookup;
    }

    /**
     * @return the root db of this database
     */
    public synchronized DatabaseNode getRoot()
    {
        return root;
    }

    /**
     * Retrieves a node by its identifier.
     *
     * @param rawUuid the id/uuid as raw text
     * @return the node, or null if not found or the id cannot be parsed as a uuid
     */
    public synchronized DatabaseNode getNode(String rawUuid)
    {
        DatabaseNode result;

        try
        {
            UUID uuid = UUID.fromString(rawUuid);
            result = getNodeByUuid(uuid);
        }
        catch (IllegalArgumentException e)
        {
            result = null;
            LOG.debug("Failed to retrieve node due to invalid uuid - raw uuid: {}", rawUuid, e);
        }

        return result;
    }

    /**
     * Retrieves a node by its identifier.
     *
     * @param uuid the node identifier
     * @return the node, or null if not found
     */
    public synchronized DatabaseNode getNodeByUuid(UUID uuid)
    {
        DatabaseNode result = lookup.get(uuid);
        return result;
    }

    /**
     * Sets the root node of this database.
     *
     * @param node the node to become root
     */
    public synchronized void setRoot(DatabaseNode node)
    {
        if (this.root != node)
        {
            // Remove old root from lookup
            lookup.remove(this.root);

            // Update to new root
            this.root = node;
        }
    }

    /**
     * Changes the password used to access the database.
     *
     * @param newPassword new password
     * @throws Exception crypto issues
     */
    public synchronized void changePassword(String newPassword) throws Exception
    {
        char[] newPasswordChars = newPassword.toCharArray();

        LOG.info("changing password...");

        updateMemoryCryptoParams(memoryCryptoParams, newPasswordChars);
        updateFileCryptoParams(fileCryptoParams, newPasswordChars);
    }

    /**
     * Performs memory encryption on data.
     *
     * Intended for encrypting data for nodes.
     *
     * @param data the data to be encrypted
     * @return the encrypted wrapper
     * @throws Exception crypto issues
     */
    public synchronized EncryptedValue encrypt(byte[] data) throws Exception
    {
        EncryptedValue value = cryptoReaderWriter.encrypt(memoryCryptoParams, data);
        return value;
    }

    /**
     * Performs memory decryption.
     *
     * Intended for decrypting data for nodes.
     *
     * @param data the encrypted wrapper
     * @return the decrypted data
     * @throws Exception if crypto fails
     */
    public synchronized byte[] decrypt(EncryptedValue data) throws Exception
    {
        byte[] result = decrypt(data, memoryCryptoParams);
        return result;
    }

    /**
     * Performs memory decryption.
     *
     * Intended for decrypting data for nodes.
     *
     * @param data the encrypted wrapper
     * @param memoryCryptoParams the crypto params; allows using params not held in the database
     * @return the decrypted data
     * @throws Exception if crypto fails
     */
    public synchronized byte[] decrypt(EncryptedValue data, CryptoParams memoryCryptoParams) throws Exception
    {
        byte[] value = cryptoReaderWriter.decrypt(memoryCryptoParams, data);
        return value;
    }

    /**
     * @return params used for file crypto; can be null if this node is not written to file
     */
    public synchronized CryptoParams getFileCryptoParams()
    {
        return fileCryptoParams;
    }

    /**
     * Updates the file crypto params for this database.
     *
     * The passed instance is cloned.
     *
     * @param fileCryptoParams the new params
     * @param password the current password
     * @throws Exception if crypto fails
     */
    public synchronized void updateFileCryptoParams(CryptoParams fileCryptoParams, char[] password) throws Exception
    {
        LOG.debug("updating file crypto params");

        // Update params
        this.fileCryptoParams = cryptoParamsFactory.clone(fileCryptoParams, password);

        // Set dirty flag
        setDirty(true);
    }

    /**
     * Updates the memory crypto params for this database.
     *
     * The passed instance is cloned.
     *
     * WARNING: this will re-encrypt all in-memory data.
     *
     * @param memoryCryptoParams the new params
     * @param password the new password
     * @throws Exception if crypto fails
     */
    public synchronized void updateMemoryCryptoParams(CryptoParams memoryCryptoParams, char[] password) throws Exception
    {
        LOG.debug("updating memory crypto params");

        // Keep ref of current params, we'll need it to re-encrypt
        CryptoParams oldMemoryCryptoParams = this.memoryCryptoParams;

        // Update to use new params
        this.memoryCryptoParams = cryptoParamsFactory.clone(memoryCryptoParams, password);

        // Re-encrypt in-memory data...
        root.rebuildCrypto(oldMemoryCryptoParams);

        // Set dirty flag
        setDirty(true);
    }


    /**
     * @return params used for in-memory crypto
     */
    public synchronized CryptoParams getMemoryCryptoParams()
    {
        return memoryCryptoParams;
    }

    /**
     * @param dirty sets whether database is dirty; true = dirty/changed, false = unchanged/saved
     */
    public synchronized void setDirty(boolean dirty)
    {
        isDirty = dirty;

        if (eventsEnabled)
        {
            dirtyEventHandlers.trigger(this, dirty);
        }
    }

    /**
     * @return true = dirty/modified, false = unchanged
     */
    public synchronized boolean isDirty()
    {
        return isDirty;
    }

    /**
     * @return collection of event handlers for when the database dirty flag changes
     */
    public EventHandlerCollection<DatabaseDirtyEventHandler> getDirtyEventHandlers()
    {
        return dirtyEventHandlers;
    }

    /**
     * Sets whether events are enabled.
     *
     * This should be used to disable events when there's large changes to the database, such as merging. This will
     * improve performance.
     *
     * @param enabled whether events are enabled
     */
    public void setEventsEnabled(boolean enabled)
    {
        this.eventsEnabled = enabled;

        // Re-trigger dirty event when enabled
        if (enabled)
        {
            setDirty(isDirty);
        }
    }

    /**
     * This shallow clone will still share references with this instance.
     *
     * @return a shallow clone of this database instance
     */
    public Database clone()
    {
        Database database = new Database(
            memoryCryptoParams.clone(), fileCryptoParams.clone()
        );

        DatabaseNode node = this.root.clone(database);
        database.setRoot(node);
        return database;
    }

    @Override
    public synchronized boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Database database = (Database) o;

        if (fileCryptoParams != null ? !fileCryptoParams.equals(database.fileCryptoParams) : database.fileCryptoParams != null)
            return false;
        if (memoryCryptoParams != null ? !memoryCryptoParams.equals(database.memoryCryptoParams) : database.memoryCryptoParams != null)
            return false;
        return root != null ? root.equals(database.root) : database.root == null;

    }

    @Override
    public synchronized int hashCode()
    {
        int result = fileCryptoParams != null ? fileCryptoParams.hashCode() : 0;
        result = 31 * result + (memoryCryptoParams != null ? memoryCryptoParams.hashCode() : 0);
        result = 31 * result + (root != null ? root.hashCode() : 0);
        return result;
    }

}
