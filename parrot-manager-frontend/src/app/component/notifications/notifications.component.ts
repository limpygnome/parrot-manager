import { Component, Renderer } from '@angular/core';

import { SyncService } from 'app/service/sync.service'

@Component({
  selector: 'notifications',
  template: ''
})
export class NotificationsComponent
{
    private syncStartEvent: Function;
    private syncFinishEvent: Function;

    constructor(
        private renderer: Renderer,
        private syncService: SyncService
    ) { }

    ngOnInit()
    {
        // Setup hook for when remote syncing starts
        this.syncStartEvent = this.renderer.listenGlobal("document", "syncStart", (event) => {
            console.log("received sync start event");

            // Update state
            this.syncService.setSyncing(true);

            // Update host being synchronized
            var options = event.data;
            var hostName = options.getName();

            // TODO deprecated
            this.syncService.setLastHostSynchronizing(hostName);

            // Show notification
            toastr.info("Syncing " + hostName);
        });

        // Setup hook for when remote syncing changes/finishes
        this.syncFinishEvent = this.renderer.listenGlobal("document", "syncFinish", (event) => {
            console.log("received sync finish event");

            // Switch state to not syncing
            this.syncService.setSyncing(false);

            // Check we have sync result (optional)
            var syncResult = event.data;

            if (syncResult != null)
            {
                var hostName = event.data.getHostName();
                var isSuccess = event.data.isSuccess();
                var isChanges = event.data.isChanges();

                // Show notification
                if (isSuccess)
                {
                    if (isChanges)
                    {
                        toastr.success(hostName + " has changes");
                    }
                    else
                    {
                        toastr.info(hostName + " has no changes");
                    }
                }
                else
                {
                    toastr.error("Failed to synchronize " + hostName);
                }
            }
        });
    }

    ngOnDestroy()
    {
        this.syncStartEvent();
        this.syncFinishEvent();
    }

}