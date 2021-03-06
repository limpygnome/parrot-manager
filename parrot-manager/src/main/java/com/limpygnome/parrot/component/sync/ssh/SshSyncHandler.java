package com.limpygnome.parrot.component.sync.ssh;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.limpygnome.parrot.component.database.DatabaseService;
import com.limpygnome.parrot.component.settings.Settings;
import com.limpygnome.parrot.component.settings.event.SettingsRefreshedEvent;
import com.limpygnome.parrot.component.sync.SyncFailureException;
import com.limpygnome.parrot.component.sync.SyncHandler;
import com.limpygnome.parrot.component.sync.SyncOptions;
import com.limpygnome.parrot.component.sync.SyncProfile;
import com.limpygnome.parrot.component.sync.SyncResult;
import com.limpygnome.parrot.lib.database.EncryptedValueService;
import com.limpygnome.parrot.library.crypto.EncryptedValue;
import com.limpygnome.parrot.library.db.Database;
import com.limpygnome.parrot.library.db.DatabaseMerger;
import com.limpygnome.parrot.library.db.DatabaseNode;
import com.limpygnome.parrot.library.log.Log;
import com.limpygnome.parrot.library.log.LogItem;
import com.limpygnome.parrot.library.log.LogLevel;
import com.limpygnome.parrot.library.io.DatabaseReaderWriter;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Currently only supports SSH, but this could be split into multiple services for different remote sync options.
 */
@Service("ssh")
public class SshSyncHandler implements SettingsRefreshedEvent, SyncHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(SshSyncHandler.class);

    private static final long DEFAULT_REMOTE_BACKUPS_RETAINED = 30L;

    @Autowired
    private SshComponent sshComponent;
    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private DatabaseReaderWriter databaseReaderWriter;
    @Autowired
    private DatabaseMerger databaseMerger;
    @Autowired
    private EncryptedValueService encryptedValueService;

    private SshSession sshSession;

    private long remoteBackupsRetained;

    @Override
    public void eventSettingsRefreshed(Settings settings)
    {
        remoteBackupsRetained = settings.getRemoteBackupsRetained().getSafeLong(DEFAULT_REMOTE_BACKUPS_RETAINED);
    }

    @Override
    public SyncProfile createProfile()
    {
        return new SshSyncProfile();
    }

    @Override
    public DatabaseNode serialize(SyncProfile profile)
    {
        Database database = databaseService.getDatabase();
        SshSyncProfile syncProfile = (SshSyncProfile) profile;

        try
        {
            // Wipe sensitive passwords if set to prompt
            if (syncProfile.isPromptUserPass())
            {
                syncProfile.setUserPass(null);
            }
            if (syncProfile.isPromptKeyPass())
            {
                syncProfile.setPrivateKeyPass(null);
            }

            // Setup new node and copy across ID
            DatabaseNode newNode = new DatabaseNode(database, profile.getName());
            if (profile.getId() != null)
            {
                UUID id = UUID.fromString(profile.getId());
                newNode.setId(id);
            }
            else
            {
                syncProfile.setId(newNode.getUuid().toString());
            }

            // Serialize as JSON string
            ObjectMapper mapper = new ObjectMapper();
            String rawJson = mapper.writeValueAsString(syncProfile);

            // Parse as JSON for sanity
            JsonParser parser = new JsonParser();
            JsonObject json = parser.parse(rawJson).getAsJsonObject();

            // Create encrypted JSON object
            EncryptedValue encryptedValue = encryptedValueService.fromJson(database, json);

            // Store in new node
            newNode.setValue(encryptedValue);
            return newNode;
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Unable to serialize profile", e);
        }
    }

    @Override
    public SyncProfile deserialize(DatabaseNode node)
    {
        Database database = databaseService.getDatabase();
        SyncProfile profile = null;

        try
        {
            // Fetch value as string
            String value = encryptedValueService.asString(database, node.getValue());

            // Check it's an SSH node
            JsonParser parser = new JsonParser();
            JsonObject json = (JsonObject) parser.parse(value);

            boolean handled = !json.has("type") || "ssh".equals(json.get("type").getAsString());

            if (handled)
            {
                // Deserialize into object
                ObjectMapper mapper = new ObjectMapper();
                mapper.disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES);
                profile = mapper.readValue(value, SshSyncProfile.class);

                // Copy node id as profile id
                profile.setId(node.getUuid().toString());
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Unable to deserialize database node to profile", e);
        }

        return profile;
    }

    @Override
    public boolean handles(SyncProfile profile)
    {
        return profile instanceof SshSyncProfile;
    }

    @Override
    public synchronized SyncResult download(SyncOptions options, SyncProfile profile)
    {
        Log log = new Log();
        boolean success = true;

        sshSession = null;

        SshSyncProfile sshProfile = (SshSyncProfile) profile;

        try
        {
            // Connect
            sshSession = sshComponent.connect(sshProfile);

            // Start download...
            SshFile source = new SshFile(sshSession, sshProfile.getRemotePath());
            String destinationPath = options.getDestinationPath();

            sshComponent.download(sshSession, source, destinationPath);
        }
        catch (Exception e)
        {
            String message = sshComponent.getExceptionMessage(e);
            log.add(new LogItem(LogLevel.ERROR, true, message));
            success = false;
            LOG.error("failed to download remote file", e);
        }
        finally
        {
            cleanup();
        }

        return new SyncResult(profile, log, success, false);
    }

    @Override
    public synchronized SyncResult test(SyncOptions options, SyncProfile profile)
    {
        sshSession = null;

        Log log = new Log();
        boolean success = true;
        boolean createdLock = false;

        SshSyncProfile sshProfile = (SshSyncProfile) profile;

        try
        {
            // connect
            sshSession = sshComponent.connect(sshProfile);

            // create lock
            createLock(null, sshProfile);
            createdLock = true;

            // check remote connection works and file exists
            SshFile file = new SshFile(sshSession, sshProfile.getRemotePath());

            if (sshComponent.checkRemotePathExists(sshSession, file))
            {
                log.add(new LogItem(LogLevel.INFO, false, "Successfully checked remote file exists"));
            }
            else
            {
                log.add(new LogItem(LogLevel.WARN, false, "Remote file does not exist - ignore if expected"));
            }
        }
        catch (Exception e)
        {
            String message = sshComponent.getExceptionMessage(e);
            log.add(new LogItem(LogLevel.ERROR, false, message));
            LOG.error("failed to test if remote file exists", e);
            success = false;
        }
        finally
        {
            if (createdLock)
            {
                cleanupLock(null, sshProfile);
            }
            cleanup();
        }

        return new SyncResult(profile, log, success, false);
    }

    @Override
    public synchronized SyncResult overwrite(SyncOptions options, SyncProfile profile)
    {
        Log log = new Log();
        boolean createdLock = false;
        boolean success = true;

        SshSyncProfile sshProfile = (SshSyncProfile) profile;

        try
        {
            // connect
            sshSession = sshComponent.connect(sshProfile);

            // create lock
            createLock(log, sshProfile);
            createdLock = true;

            String localPath = databaseService.getPath();
            SshFile fileRemote = new SshFile(sshSession, sshProfile.getRemotePath());
            SshFile fileRemoteSyncBackup = fileRemote.clone().postFixFileName(".sync");

            // check if database exists
            if (!sshComponent.checkRemotePathExists(sshSession, fileRemote))
            {
                log.add(new LogItem(LogLevel.ERROR, false, "Remote file does not exist - ignore if expected"));
            }
            else
            {
                // move current database as sync backup (in case upload fails)
                sshComponent.rename(sshSession, fileRemote, fileRemoteSyncBackup);

                // upload current database
                sshComponent.upload(sshSession, localPath, fileRemote);

                // delete or convert to backup
                convertToRemoteBackupOrDelete(log, fileRemote, fileRemoteSyncBackup);
            }
        }
        catch (Exception e)
        {
            success = false;

            String message = sshComponent.getExceptionMessage(e);
            log.add(new LogItem(LogLevel.ERROR, true, message));

            LOG.error("failed to overwrite remote database", e);
        }
        finally
        {
            // cleanup lock
            if (createdLock)
            {
                cleanupLock(log, sshProfile);
            }

            // disconnect
            cleanup();
        }

        SyncResult result = new SyncResult(profile, log, success, false);
        return result;
    }

    @Override
    public synchronized SyncResult unlock(SyncOptions options, SyncProfile profile)
    {
        Log log = new Log();
        boolean success;

        SshSyncProfile sshProfile = (SshSyncProfile) profile;

        try
        {
            // connect
            sshSession = sshComponent.connect(sshProfile);

            // remove lock file
            SshFile fileLock = new SshFile(sshSession, sshProfile.getRemotePath()).postFixFileName(".lock");
            sshComponent.remove(sshSession, fileLock);
            success = true;

            log.add(new LogItem(LogLevel.INFO, false, "Removed remote database lock file"));
        }
        catch (JSchException | SftpException e)
        {
            LOG.error("failed to remove database lock", e);
            success = false;
            log.add(new LogItem(LogLevel.ERROR, false, "Failed to remove remote database lock file - " + e.getMessage()));
        }
        finally
        {
            cleanup();
        }

        return new SyncResult(sshProfile, log, success, false);
    }

    @Override
    public synchronized SyncResult sync(SyncOptions options, SyncProfile profile)
    {
        Log log = new Log();
        boolean success = true;
        boolean dirty = false;
        boolean createdLock = false;

        SshSyncProfile sshProfile = (SshSyncProfile) profile;

        Database database = databaseService.getDatabase();

        // alter destination path for this host
        int fullHostNameHash = (sshProfile.getHost() + sshProfile.getPort()).hashCode();
        String syncPath = options.getDestinationPath();
        syncPath = syncPath + "." + fullHostNameHash + "." + System.currentTimeMillis() + ".sync";

        // fetch current path to database
        String currentPath = databaseService.getPath();

        // begin sync process...
        try
        {
            // connect
            LOG.info("sync - connecting");
            sshSession = sshComponent.connect(sshProfile);

            SshFile source = new SshFile(sshSession, sshProfile.getRemotePath());
            SshFile fileSyncBackup = new SshFile(sshSession, sshProfile.getRemotePath()).postFixFileName(".sync");

            // create lock file
            createLock(log, sshProfile);
            createdLock = true;

            // check whether an old renamed file exists; if so, restore it
            if (sshComponent.checkRemotePathExists(sshSession, fileSyncBackup))
            {
                log.add(new LogItem(LogLevel.ERROR, true, "Old file from previous failed sync found"));

                // move current file to corrupted
                SshFile fileCorrupted = source.clone().postFixFileName(".corrupted." + System.currentTimeMillis());
                sshComponent.rename(sshSession, source, fileCorrupted);
                log.add(new LogItem(LogLevel.INFO, true, "Corrupted file moved to " + fileCorrupted.getFileName()));

                // restore backup file
                sshComponent.rename(sshSession, fileSyncBackup, source);
                log.add(new LogItem(LogLevel.INFO, true, "Sync backup file restored"));
            }

            // start download...
            LOG.info("sync - downloading");

            String error = sshComponent.download(sshSession, source, syncPath);

            if (error == null)
            {
                String remotePassword = options.getDatabasePassword();

                // load remote database
                LOG.info("sync - loading remote database");
                Database remoteDatabase = databaseReaderWriter.open(syncPath, remotePassword.toCharArray());

                // perform merge and check if any change occurred...
                LOG.info("sync - performing merge...");
                log = databaseMerger.merge(remoteDatabase, database, remotePassword.toCharArray());

                // save current database if dirty
                if (database.isDirty())
                {
                    LOG.info("sync - database(s) dirty, saving...");
                    databaseReaderWriter.save(database, currentPath);

                    // reset dirty flag
                    database.setDirty(false);

                    // store dirty for event
                    dirty = true;
                }

                // upload to remote source if database is out of date
                if (log.isRemoteOutOfDate())
                {
                    LOG.info("sync - uploading to remote host...");

                    // move current file as backup in case upload fails
                    sshComponent.rename(sshSession, source, fileSyncBackup);
                    log.add(new LogItem(LogLevel.DEBUG, false, "Renamed remote database in case sync fails"));

                    // upload new file
                    sshComponent.upload(sshSession, currentPath, source);
                    log.add(new LogItem(LogLevel.INFO, false, "Uploaded database"));

                    // delete or create backup out of sync file
                    convertToRemoteBackupOrDelete(log, source, fileSyncBackup);
                }
                else
                {
                    LOG.info("sync - neither database is dirty");
                }
            }
            else
            {
                LOG.info("sync - uploading current database");

                log.add(new LogItem(LogLevel.DEBUG, false, "Uploading current database, as does not exist remotely"));

                sshComponent.upload(sshSession, currentPath, source);

                log.add(new LogItem(LogLevel.INFO, false, "Uploaded database for first time"));
            }
        }
        catch (Exception e)
        {
            if (e instanceof InterruptedException)
            {
                throw new RuntimeException("Sync aborted");
            }

            // Convert to failed merge
            String message = sshComponent.getExceptionMessage(e);
            log = new Log();
            log.add(new LogItem(LogLevel.ERROR, true, message));

            success = false;
            LOG.error("sync - exception", e);
        }
        finally
        {
            // remove remote lock
            if (createdLock)
            {
                cleanupLock(log, sshProfile);
            }

            // cleanup sync file
            File syncFile = new File(syncPath);

            if (syncFile.exists())
            {
                syncFile.delete();
            }

            // disconnect
            try
            {
                cleanup();
            }
            catch (Exception e)
            {
                LOG.error("failed cleanup", e);
            }
        }

        // raise event with result
        SyncResult syncResult = new SyncResult(profile, log, success, dirty);
        return syncResult;
    }

    @Override
    public boolean canAutoSync(SyncOptions options, SyncProfile profile)
    {
        SshSyncProfile syncProfile = (SshSyncProfile) profile;
        return !syncProfile.isPromptKeyPass() && !syncProfile.isPromptUserPass();
    }

    private synchronized void cleanup()
    {
        if (sshSession != null)
        {
            // destroy session
            sshSession.dispose();
            sshSession = null;

            LOG.info("ssh session cleaned up");
        }
    }

    private void createLock(Log log, SshSyncProfile options) throws SyncFailureException
    {
        try
        {
            SshFile fileLock = new SshFile(sshSession, options.getRemotePath()).postFixFileName(".lock");

            // create lock file
            String tmpDir = System.getProperty("java.io.tmpdir");
            File fileLocalLock = new File(tmpDir, "parrot-manager.lock");
            fileLocalLock.createNewFile();

            // check lock file doesn't already exist...
            boolean isLocked;
            int attempts = 0;

            do
            {
                LOG.info("checking for database lock - attempt {}", attempts);

                isLocked = sshComponent.checkRemotePathExists(sshSession, fileLock);
                attempts++;

                if (isLocked)
                {
                    if (log != null)
                    {
                        log.add(new LogItem(LogLevel.DEBUG, false, "Remote lock exists - attempt " + attempts));
                    }
                    LOG.info("remote database lock exists, sleeping...");
                    Thread.sleep(1000);
                }
            }
            while (isLocked && attempts < 10);

            if (isLocked)
            {
                throw new RuntimeException("Remote database lock file exists and timed-out waiting for it to disappear. Use unlock database button, or manually remove lock file.");
            }

            // upload
            sshComponent.upload(sshSession, fileLocalLock.getAbsolutePath(), fileLock);

            if (log != null)
            {
                log.add(new LogItem(LogLevel.DEBUG, false, "Created remote lock"));
            }
            LOG.info("remote database lock file created");
        }
        catch (JSchException e)
        {
            String message = sshComponent.getExceptionMessage(e);
            throw new SyncFailureException(message, e);
        }
        catch (IOException e)
        {
            throw new SyncFailureException("Failed to create lock file locally", e);
        }
        catch (SftpException | InterruptedException e)
        {
            throw new SyncFailureException("Failed to create lock file - " + e.getMessage(), e);
        }
    }

    private void cleanupLock(Log log, SshSyncProfile options)
    {
        try
        {
            SshFile fileLock = new SshFile(sshSession, options.getRemotePath()).postFixFileName(".lock");
            sshComponent.remove(sshSession, fileLock);

            if (log != null)
            {
                log.add(new LogItem(LogLevel.DEBUG, false, "Removed remote lock file"));
            }
            LOG.info("remote database lock file removed");
        }
        catch (SftpException | JSchException e)
        {
            if (log != null)
            {
                log.add(new LogItem(LogLevel.ERROR, false, "Failed to remove remote lock"));
            }
            LOG.error("failed to cleanup database lock", e);
        }
    }

    private void convertToRemoteBackupOrDelete(Log log, SshFile databaseFile, SshFile currentFile) throws SftpException, JSchException
    {
        SshFile backupFile = databaseFile.clone().preFixFileName(".").postFixFileName("." + System.currentTimeMillis());

        // convert if backups enabled, otherwise just delete it
        if (remoteBackupsRetained > 0)
        {
            // rename as backup
            sshComponent.rename(sshSession, currentFile, backupFile);
            log.add(new LogItem(LogLevel.DEBUG, false, "Created new backup - " + backupFile.getFileName()));

            // fetch list of files
            SshFile parent = backupFile.getParent(sshSession);
            String escapedFileName = Pattern.quote("." + databaseFile.getFileName() + ".");
            List<SshFile> files = sshComponent.list(
                    sshSession, parent, sshFile -> sshFile.getFileName().matches(escapedFileName + ".[0-9]+")
            );

            // drop those outside retained limit
            if (files.size() > remoteBackupsRetained)
            {
                log.add(new LogItem(LogLevel.DEBUG, false, "Too many remote backups - " + files.size() + " / " + remoteBackupsRetained));

                int culled = files.size() - (int) remoteBackupsRetained;
                for (int i = 0; i < culled; i++)
                {
                    SshFile file = files.get(i);
                    sshComponent.remove(sshSession, file);
                    log.add(new LogItem(LogLevel.DEBUG, false, "Removed remote file " + file.getFileName()));
                }
            }
            else
            {
                log.add(new LogItem(LogLevel.DEBUG, false, "No remote backups culled - " + files.size() + " / " + remoteBackupsRetained));
            }
        }
        else
        {
            // remove file
            sshComponent.remove(sshSession, backupFile);
            log.add(new LogItem(LogLevel.DEBUG, false, "Removed remote file " + backupFile.getFileName()));
        }
    }

}
