package com.limpygnome.parrot.component.file;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Used to resolve paths and perform common operations for file related functions.
 *
 * TODO: unit test
 */
@Component
public class FileComponent
{
    private static final Logger LOG = LogManager.getLogger(FileComponent.class);

    /**
     * Used to resolve path shortcuts.
     *
     * ~/ will be converted to home path
     * 
     * @param path the raw path
     * @return fully resolved path
     */
    public String resolvePath(String path)
    {
        if (path.startsWith("~/") && path.length() > 2)
        {
            // TODO: test on windows, could be flawed...
            String homeDirectory = System.getProperty("user.home");
            String pathSeparator = System.getProperty("file.separator");

            path = homeDirectory + pathSeparator + path.substring(2);
        }

        return path;
    }

    /**
     * Builds the full path to a preference file.
     *
     * @param fileName file name
     * @return the full path
     */
    public File resolvePreferenceFile(String fileName)
    {
        File preferenceRootFile = getPreferenceFileRoot();
        File result = new File(preferenceRootFile, fileName);
        return result;
    }

    /**
     * @return root file for storing preferences / user data for the current user
     */
    public File getPreferenceFileRoot()
    {
        // TODO: need to consider windows
        String homeDir = System.getProperty("user.home");
        File result = new File(homeDir + "/.config/parrot-manager");

        // Check the root exists, otherwise make it
        if (!result.exists())
        {
            boolean isSuccess = result.mkdirs();

            if (isSuccess)
            {
                LOG.info("created preferences directory - path: {}", result.getAbsolutePath());
            }
            else
            {
                LOG.error("failed to create preferences directory - path: {}", result.getAbsolutePath());
            }
        }
        else
        {
            LOG.debug("preferences directory already exists");
        }

        return result;
    }

}