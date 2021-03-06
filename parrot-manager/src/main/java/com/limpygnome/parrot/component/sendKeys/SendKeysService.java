package com.limpygnome.parrot.component.sendKeys;

import com.limpygnome.parrot.component.database.DatabaseService;
import com.limpygnome.parrot.component.settings.Settings;
import com.limpygnome.parrot.component.settings.event.SettingsRefreshedEvent;
import com.limpygnome.parrot.lib.database.EncryptedValueService;
import com.limpygnome.parrot.component.ui.WebStageInitService;
import com.limpygnome.parrot.component.ui.WebViewStage;
import com.limpygnome.parrot.library.crypto.EncryptedValue;
import com.limpygnome.parrot.library.db.Database;
import com.limpygnome.parrot.library.db.DatabaseNode;
import com.limpygnome.parrot.library.db.DatabaseNodeHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Robot;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * A service for sending keys to another application.
 */
@Service
public class SendKeysService implements SettingsRefreshedEvent
{
    private static final Logger LOG = LoggerFactory.getLogger(SendKeysService.class);

    // Components
    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private WebStageInitService initService;
    @Autowired
    private EncryptedValueService encryptedValueService;
    @Autowired
    private KeyboardLayoutRepository keyboardLayoutRepository;

    // State
    // -- Current keyboard layout to be used
    private KeyboardLayout keyboardLayout;
    // -- Indicates if a listener has been added for when the application is minimized
    private boolean isMinimizeHooked = false;
    // -- Data to be sent when the app is next minimized
    private String pendingData = null;
    // -- The encrypted value pending being sent
    private UUID pendingEncryptedValueId = null;
    // -- Cache of available keyboards
    private KeyboardLayout[] keyboardLayoutsCache = new KeyboardLayout[0];

    // Settings
    private String keyboardName;

    /**
     * Reloads the keyboard layouts.
     *
     * @return array of error messages, or empty if none
     */
    public String[] reload()
    {
        String[] messages = keyboardLayoutRepository.reload();

        // update cache
        updateKeyboardLayoutsCache();

        return messages;
    }

    /**
     * @return array of all available keyboard layouts
     */
    public synchronized KeyboardLayout[] getKeyboardLayouts()
    {
        return keyboardLayoutsCache;
    }

    /**
     * @return the current keyboard layout
     */
    public KeyboardLayout getKeyboardLayout()
    {
        return keyboardLayout;
    }

    /**
     * Sends keys once the application is minimized.
     *
     * @param rawNodeId raw node id
     * @param rawEncryptedValueId the raw id of the encrypted value (optional)
     */
    public synchronized void send(String rawNodeId, String rawEncryptedValueId) throws Exception
    {
        EncryptedValue encryptedValue = null;
        boolean queued = false;

        // Retrieve encrypted value
        Database database = databaseService.getDatabase();
        DatabaseNode node = database.getNode(rawNodeId);

        if (rawEncryptedValueId != null && rawEncryptedValueId.length() > 0)
        {
            DatabaseNodeHistory history = node.getHistory();
            encryptedValue = history.fetchById(rawEncryptedValueId);
        }
        else if (node != null)
        {
            encryptedValue = node.getValue();
        }
        else
        {
            LOG.warn("failed to find node for send keys - id: {}", rawNodeId);
        }

        // Handle encrypted value
        if (encryptedValue != null)
        {
            // Reset if same encrypted value - allows user to toggle sending keys
            if (pendingEncryptedValueId != null && pendingEncryptedValueId.equals(encryptedValue.getUuid()))
            {
                pendingData = null;
                pendingEncryptedValueId = null;

                LOG.info("reset pending send keys");
            }
            else
            {
                LOG.info("queueing sending keys");

                // Fetch and store decrypted value and id
                pendingEncryptedValueId = encryptedValue.getUuid();
                pendingData = encryptedValueService.asString(database, encryptedValue);
                queued = true;

                // Ensure we know when the app is minimized
                hookStageMinimized();
            }
        }
        else
        {
            pendingData = null;
            pendingEncryptedValueId = null;

            LOG.debug("null encrypted value, not sending keys");
        }

        // Raise event to trigger any front-end changes
        SendKeysChangeEvent event = new SendKeysChangeEvent(rawNodeId, rawEncryptedValueId, queued);
        initService.triggerEvent("document", "sendKeys.change", event);
    }

    /**
     * Test for emulating keys, but keys are sent immediately.
     *
     * This is intended for testing emulating keys within the app.
     *
     * @param text text to be tested
     * @throws Exception thrown if unable to emulate keys
     */
    public synchronized void sendTest(String text) throws Exception
    {
        pendingData = text;
        simulateKeys();
    }

    public void openLocalUserDirectory()
    {
        openDirectory(keyboardLayoutRepository.getLocalDirectory());
    }

    public void openWorkingDirectory()
    {
        openDirectory(keyboardLayoutRepository.getWorkingDirectory());
    }

    /**
     * Triggers service to refresh the current keyboard layout to be used for sending keys.
     */
    @PostConstruct
    public void refreshKeyboardLayout()
    {
        // update current keyboard to use
        keyboardLayout = keyboardLayoutRepository.determineBest(keyboardName);

        // initialise/update cache
        updateKeyboardLayoutsCache();

        LOG.info("keyboard layout: {}", (keyboardLayout != null ? keyboardLayout.getName() : "none"));
    }

    @Override
    public void eventSettingsRefreshed(Settings settings)
    {
        keyboardName = settings.getKeyboardLayout().getValue();
        refreshKeyboardLayout();
    }

    private void updateKeyboardLayoutsCache()
    {
        this.keyboardLayoutsCache = keyboardLayoutRepository.getKeyboardLayouts();
    }

    private void openDirectory(File file)
    {
        try
        {
            Desktop.getDesktop().open(file);
        }
        catch (IOException e)
        {
            String path = file.getAbsolutePath();
            LOG.error("failed to open directory - path: {}", path, e);
        }
    }

    private synchronized void hookStageMinimized()
    {
        if (!isMinimizeHooked)
        {
            isMinimizeHooked = true;

            // Hook stage for minify (or dubbed iconified) event
            WebViewStage stage = initService.getStage();
            stage.iconifiedProperty().addListener((observable, oldValue, newValue) ->
            {
                LOG.debug("iconified property changed - old: {}, new: {}", oldValue, newValue);

                if (newValue)
                {
                    simulateKeys();
                }
            });

            stage.focusedProperty().addListener((observable, oldValue, newValue) ->
            {
                LOG.debug("foused property changed - old: {}, new: {}", oldValue, newValue);

                if (!newValue)
                {
                    simulateKeys();
                }
            });

            LOG.debug("iconified hooked");
        }
    }

    private synchronized void simulateKeys()
    {
        if (pendingData != null)
        {
            try
            {
                LOG.debug("simulating keys...");

                /*
                    Delay needed for some window managers to switch windows completely / gain focus etc.

                    This should be more than sufficient time, even for slow machines. If this ever becomes an issue,
                    the delay/time can be moved to a global setting.
                 */

                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException e) { }


                /*
                    Create robot instance for key emulation

                    Auto-delay not great, but Mac High Sierra does not work consistently without a delay. Likely
                    JRE bug.
                 */

                Robot robot = new Robot();
                robot.setAutoDelay(10);

                // Simulate key press for each char
                for (char key : pendingData.toCharArray())
                {
                    type(robot, key);
                }

                // Wipe stored data
                pendingEncryptedValueId = null;
                pendingData = null;

                // Raise event for changed state
                SendKeysChangeEvent event = new SendKeysChangeEvent(null, null, false);
                initService.triggerEvent("document", "sendKeys.change", event);

                LOG.debug("finished simulating keys");

            }
            catch (AWTException e)
            {
                LOG.error("failed to send keys", e);
            }
        }
        else
        {
            LOG.debug("skipped simulating keys, pending data is empty");
        }
    }

    private synchronized void type(Robot robot, char key)
    {
        if (keyboardLayout == null)
        {
            throw new IllegalStateException("no keyboard layout available");
        }

        int[] keyCodes = keyboardLayout.convert(key);
        type(robot, keyCodes);
    }

    private synchronized void type(Robot robot, int... keyCodes)
    {
        // press all the keys down
        for (int keyCode : keyCodes)
        {
            robot.keyPress(keyCode);
        }

        // release them all
        for (int keyCode : keyCodes)
        {
            robot.keyRelease(keyCode);

            LOG.info("SIMULATED " + keyCode);
        }
    }

}
