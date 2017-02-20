package com.limpygnome.parrot;

import com.limpygnome.parrot.service.BackupService;
import com.limpygnome.parrot.service.DatabaseService;
import com.limpygnome.parrot.service.RandomGeneratorService;
import com.limpygnome.parrot.service.RemoteSshFileService;
import com.limpygnome.parrot.service.RuntimeService;
import com.limpygnome.parrot.ui.WebViewStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The facade for holding instances of common (shared) runtime services.
 *
 * TODO: get rid of accessors, should be no need for it; we'll almost just want an auto-wiring bean/instance for
 * inecting beans into stage
 */
@Component
public class Controller
{
    // Services
    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private RandomGeneratorService randomGeneratorService;
    @Autowired
    private RemoteSshFileService remoteSshFileService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private BackupService backupService;

    // Properties
    private boolean developmentMode;

    public Controller()
    {
        this.developmentMode = false;
    }

    public Controller(boolean developmentMode)
    {
        this.developmentMode = developmentMode;
    }

    /**
     * Attaches this controller instance to a stage. Can only be done once.
     */
    public void attach(WebViewStage stage)
    {
        // Pass stage onto services which need to know about it
        runtimeService.setStage(stage);

        // Inject required services into front-end
        // WARNING: due to JavaFX "bug", never pass newly constructed instances here
        stage.exposeJsObject("runtimeService", runtimeService);
        stage.exposeJsObject("databaseService", databaseService);
        stage.exposeJsObject("randomGeneratorService", randomGeneratorService);
        stage.exposeJsObject("remoteSshFileService", remoteSshFileService);
        stage.exposeJsObject("backupService", backupService);
    }

    /**
     * @return indicates if running in development mode
     */
    public boolean isDevelopmentMode()
    {
        return developmentMode;
    }

}
