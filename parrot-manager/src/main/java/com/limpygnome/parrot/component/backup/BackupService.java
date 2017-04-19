package com.limpygnome.parrot.component.backup;

import com.limpygnome.parrot.library.db.Database;
import com.limpygnome.parrot.library.io.DatabaseReaderWriter;
import com.limpygnome.parrot.component.settings.Settings;
import com.limpygnome.parrot.component.database.DatabaseService;
import com.limpygnome.parrot.component.session.SessionService;
import com.limpygnome.parrot.component.settings.SettingsService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Arrays;

/**
 * Used to create backups of a database.
 */
@Service
public class BackupService
{
    private static final Logger LOG = LogManager.getLogger(BackupService.class);

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private DatabaseReaderWriter databaseReaderWriter;
    @Autowired
    private SessionService sessionService;

    /**
     * Creates a new backup.
     *
     * @return error, null if successful
     */
    public String create()
    {
        String errorMessage = null;

        // Check database open and backups are enabled
        Settings settings = settingsService.getSettings();
        boolean isEnabled = settings.getAutomaticBackupsOnSave().getValue();
        File currentFile = databaseService.getFile();

        if (currentFile != null && isEnabled)
        {
            LOG.info("creating backup...");

            try
            {
                // Fetch the database currently open
                Database database = databaseService.getDatabase();

                // Check if max retained databases has been met
                checkRetainedDatabases();

                // Build path
                File currentParentFile = currentFile.getParentFile();
                File backupFile = new File(currentParentFile, "." + databaseService.getFileName() + "." + System.currentTimeMillis());

                // Save as backup...
                databaseReaderWriter.save(database, backupFile);

                LOG.info("backup created - name: {}", backupFile.getAbsolutePath());
            }
            catch (Exception e)
            {
                errorMessage = "Failed to create backup - " + e.getMessage();
                LOG.error("failed to create backup", e);
            }
        }

        return errorMessage;
    }

    /**
     * Fetches a list of existing backups.
     *
     * This will look for files in the same path as the current database, starting with the same name.
     *
     * @return array of backup file-names
     */
    public BackupFile[] fetch()
    {
        File[] files = fetchFiles();

        // Translate to view model
        BackupFile[] backupFiles = Arrays.stream(files).map(file -> new BackupFile(file)).toArray(size -> new BackupFile[size]);

        // Store in session to prevent GC
        sessionService.put("backups", backupFiles);

        return backupFiles;
    }

    /**
     * Deletes a backup.
     *
     * @param backupFile the instance to be deleted
     * @return error message; null if successful
     */
    public String delete(BackupFile backupFile)
    {
        String result = null;
        File file = new File(backupFile.getPath());

        if (!file.delete())
        {
            result = "Unable to delete file (unknown reason)";
        }

        return result;
    }

    private File[] fetchFiles()
    {
        File[] files;

        // Fetch backup files
        File currentFile = databaseService.getFile();

        if (currentFile != null)
        {
            File parentFile = currentFile.getParentFile();
            String currentName = currentFile.getName();
            files = parentFile.listFiles((dir, name) -> name.startsWith("." + currentName));
        }
        else
        {
            files = new File[0];
            LOG.warn("attempted to fetch backup files when database closed");
        }

        return files;
    }

    private void checkRetainedDatabases()
    {
        long maxRetained = settingsService.getSettings().getAutomaticBackupsRetained().getValue();

        if (maxRetained > 0)
        {
            File[] backupFiles = fetchFiles();

            if (backupFiles.length >= maxRetained)
            {
                // Subtract one as we're now making a new backup, hence we only want n backups afterwards
                deleteOldestRetainedBackups(backupFiles, maxRetained - 1);
            }
        }
    }

    private void deleteOldestRetainedBackups(File[] backupFiles, long maxRetained)
    {
        Arrays.stream(backupFiles)
                .sorted((o1, o2) -> (int) (o1.lastModified() - o2.lastModified()) )
                .limit(backupFiles.length - maxRetained)
                .forEach(file -> {
                    file.delete();
                    LOG.info("deleted old retained file (max reached) - name: {}", file.getName());
                });
    }

}
