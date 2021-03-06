import { Component, Renderer } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { DatabaseService } from 'app/service/database.service'
import { ClipboardService } from 'app/service/clipboard.service'
import { SyncService } from 'app/service/sync.service'
import { SyncProfileService } from 'app/service/syncProfile.service'
import { SyncResultService } from 'app/service/syncResult.service'

@Component({
    templateUrl: 'sync.component.html',
    styleUrls: ['sync.component.css']
})
export class SyncComponent {

    public profiles: any;
    private oldChangeLog: string;
    public syncResults: any;

    private syncResultChangeEvent: Function;
    private syncProfileChangeEvent: Function;

    constructor(
        public syncService: SyncService,
        public syncProfileService: SyncProfileService,
        public syncResultService: SyncResultService,
        public databaseService: DatabaseService,
        public clipboardService: ClipboardService,
        public router: Router,
        public fb: FormBuilder,
        private renderer: Renderer
    ) { }

    ngOnInit()
    {
        // Hook for sync result changes
        this.syncResultChangeEvent = this.renderer.listenGlobal("document", "syncResults.change", (event) => {
            this.syncResults = this.syncResultService.getResults();
        });

        // Hook for sync profile changes
        this.syncProfileChangeEvent = this.renderer.listenGlobal("document", "syncProfiles.change", (event) => {
            this.profiles = this.syncProfileService.fetch();
         });

        // Fetch last results
        this.syncResults = this.syncResultService.getResults();

        // Fetch profiles
        this.profiles = this.syncProfileService.fetch();
    }

    ngOnDestroy()
    {
        this.syncResultChangeEvent();
        this.syncProfileChangeEvent();
    }

    trackChildren(index, profile)
    {
        return profile ? profile.id : null;
    }

    sync(profile, promptForAuth)
    {
        this.syncService.sync(profile, promptForAuth);
    }

    isSyncing() : boolean
    {
        return this.syncService.isSyncing();
    }

    abort()
    {
        console.log("aborting sync");
        this.syncService.abort();
    }

    copySyncLogToClipboard()
    {
        // Fetch log as text
        var text = this.syncResultService.getResultsAsText();

        // Update clipboard
        this.clipboardService.setText(text);
    }

}
