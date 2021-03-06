package com.limpygnome.parrot.component.ui;


import com.limpygnome.parrot.component.ui.preferences.WindowPreferencesInitService;
import javafx.event.EventHandler;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Close handler for {@link WebViewStage}.
 *
 * This will prevent the window from exiting and enable the front-end to handle an exit. If a database is open and
 * there's unsaved changes, the front-end will prevent an exit and display a prompt. Otherwise a JVM exit is triggered.
 *
 * If a request to exit is made five times within five seconds, an exit is allowed. This is in the event of front-end
 * JavaScript breaking, so that the user can forcibly exit.
 */
public class CloseHandler implements EventHandler<WindowEvent>
{
    private static final Logger LOG = LoggerFactory.getLogger(CloseHandler.class);

    private static final long FORCE_EXIT_TIMEOUT_MILLISECONDS = 5000;
    private static final long CLICKS_FOR_EXIT = 5;

    private WebViewStage stage;
    private WindowPreferencesInitService windowPreferencesInitService;

    private long lastExitRequest;
    private long closeRequestsCounter;

    CloseHandler(WebViewStage stage, WindowPreferencesInitService windowPreferencesInitService)
    {
        this.stage = stage;
        this.windowPreferencesInitService = windowPreferencesInitService;
    }

    @Override
    public void handle(WindowEvent event)
    {
        // check whether exit has been spammed
        long currentTime = System.currentTimeMillis();

        /*
            reset or increment counter for forced exit (in the event of js breaking), so that if exit is clicked so many
            times within the timeout period, an exit is allowed
         */
        if (currentTime - lastExitRequest > FORCE_EXIT_TIMEOUT_MILLISECONDS)
        {
            closeRequestsCounter = 1;
        }
        else
        {
            closeRequestsCounter++;
        }

        // update last request
        lastExitRequest = currentTime;

        // consume close request if threshold not met for forced exit
        if (closeRequestsCounter < CLICKS_FOR_EXIT)
        {
            // prevent exit by consuming event
            event.consume();

            // trigger event for JS to handle action
            stage.triggerEvent("document", "nativeExit", null);

            LOG.info("request to exit consumed - force exit count: {}/{}", closeRequestsCounter, CLICKS_FOR_EXIT);

            // Safe window preferences
            if (closeRequestsCounter == 1)
            {
                windowPreferencesInitService.save(stage);
            }
        }
        else
        {
            // force end jvm with error code
            System.exit(1);
        }
    }

}
