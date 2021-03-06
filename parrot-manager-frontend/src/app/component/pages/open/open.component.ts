import { Component } from '@angular/core';
import { Router } from '@angular/router';

import { DatabaseService } from 'app/service/database.service'
import { RuntimeService } from 'app/service/runtime.service'
import { RecentFileService } from 'app/service/recentFile.service'
import { SettingsService } from 'app/service/settings.service'

@Component({
    templateUrl: 'open.component.html',
    providers: [RecentFileService, SettingsService],
    styleUrls: ['open.component.css']
})
export class OpenComponent {

    static isStartup : boolean = true;
    errorMessage: string;
    recentFiles: any;

    constructor(
        public runtimeService: RuntimeService,
        private databaseService: DatabaseService,
        private recentFileService: RecentFileService,
        private settingsService: SettingsService,
        private router: Router
    ) { }

    ngOnInit()
    {
        // Fetch recently opened files
        this.refreshRecentFiles();

        // Check whether database open, if so redirect to viewer (mainly for development)
        if (this.databaseService.isOpen())
        {
            console.log("database already open, redirecting to viewer");
            this.router.navigate(["/viewer"]);
        }
        else
        {
            // Open database on startup if there's recent files and enabled...
            if (OpenComponent.isStartup && this.recentFiles.length > 0)
            {
                var isEnabled = this.settingsService.fetch("recentFilesOpenLastOnStartup");

                if (isEnabled)
                {
                    this.openFile(this.recentFiles[0].path);
                }
            }

            // Set startup flag, hence the above will only occur on startup
            OpenComponent.isStartup = false;
        }
    }

    refreshRecentFiles()
    {
        this.recentFiles = this.recentFileService.fetch();
    }

    chooseDatabaseFile() : void
    {
        // Open dialogue and read file
        var path = this.runtimeService.pickFile("Open existing database", null, false);
        this.openFile(path);
    }

    openFile(path)
    {
        // Open with password prompt
        this.databaseService.openWithPrompt(path, (message) => {

            // Check if failure message
            if (message == null)
            {
                console.log("successfully opened database, redirecting to navigator...");
                this.router.navigate(["/viewer"]);
            }
            else
            {
                this.errorMessage = message;
                console.log("failed to open database - " + message);
            }

        });
    }

    // TODO pass path
    deleteRecentFile(recentFile)
    {
        // delete it...
        this.recentFileService.delete(recentFile.path);

        // refresh list
        this.refreshRecentFiles();
    }

    trackChildren(index, recentFile)
    {
        return recentFile ? recentFile.name : null;
    }

}
