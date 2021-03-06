import { Component } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router'

import { SettingsService } from 'app/service/settings.service'
import { DatabaseService } from 'app/service/database.service'
import { ThemeService } from 'app/service/theme.service'

@Component({
    templateUrl: "settings.component.html",
    styleUrls: ["settings.component.css"]
})
export class SettingsComponent
{
    public currentTab: string;
    public lastSettings;
    public paramsObserver: any;

    public globalSettingsForm = this.fb.group({
        recentFilesEnabled: [false],
        recentFilesOpenLastOnStartup: [false],
        automaticBackupsOnSave: [false],
        automaticBackupsRetained: [""],
        automaticBackupDelay: [""],
        remoteSyncInterval: [""],
        remoteSyncIntervalEnabled: [false],
        remoteSyncOnOpeningDatabase: [false],
        remoteSyncOnChange: [false],
        remoteSyncOnChangeDelay: [""],
        theme: [""],
        saveWindowState: [""],
        inactivityTimeout: [""],
        wipeClipboardDelay: [""],
        autoSave: [true],
        mergeLogShowDetail: [false],
        keyboardLayout: [""],
        remoteBackupsRetained: [""],
        ignoreJavaVersion: [""]
    });

    constructor(
        public settingsService: SettingsService,
        public databaseService: DatabaseService,
        public themeService: ThemeService,
        public fb: FormBuilder,
        private route: ActivatedRoute
    ) { }

    ngOnInit()
    {
        // Check which page to show by default
        this.currentTab = (this.databaseService.isOpen() ? "changePassword" : "recentFiles");

        // Subscribe for params
        this.paramsObserver = this.route.params.subscribe(params => {
            var tab = params.tab;
            if (tab != null)
            {
                this.currentTab = tab;
            }
        });

        // Load settings
        this.populateSettings();

        // Subscribe to changes
        this.globalSettingsForm.valueChanges.subscribe(form =>
        {
            // Check settings have changed, as binding form controls between components will trigger this event
            if (JSON.stringify(form) != JSON.stringify(this.lastSettings))
            {
                console.log("settings changed");
                console.log(JSON.stringify(form));
                console.log(JSON.stringify(this.lastSettings));

                // Save changes
                this.settingsService.save(form);

                // Update last settings
                this.refreshLastSettings();
            }
            else
            {
                console.log("settings unchanged, ignored");
            }
        });
    }

    ngOnDestroy()
    {
        this.paramsObserver.unsubscribe();
    }

    populateSettings()
    {
        console.log("populating form with settings...");

        // Read existing settings
        this.refreshLastSettings();

        // Apply to form
        this.globalSettingsForm.patchValue(this.lastSettings);
    }

    refreshLastSettings()
    {
        // Read existing settings
        var settings = this.settingsService.fetchAll();
        this.lastSettings = settings;
    }

    save()
    {
        var form = this.globalSettingsForm;

        if (form.valid)
        {
            console.log("saving settings...");

            // Save settings
            var newSettings = form.value;
            var errorMessage = this.settingsService.save(newSettings);

            // Display notification
            if (errorMessage == null)
            {
                toastr.success("Settings saved");
            }
            else
            {
                toastr.error(errorMessage);
            }
        }
        else
        {
            console.log("settings form not valid");
        }
    }

    globalResetToDefault()
    {
        console.log("resetting to default settings");
        var errorMessage = this.settingsService.reset();

        if (errorMessage != null)
        {
            toastr.error(errorMessage);
        }
        else
        {
            toastr.info("Settings reset to default");
        }

        // reset theme
        var theme = this.settingsService.fetch("theme");
        this.themeService.set(theme);

        this.populateSettings();
    }

}
