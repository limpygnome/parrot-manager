import { Injectable } from '@angular/core';

import { DatabaseService } from 'app/service/database.service'

@Injectable()
export class ImportExportService
{
    importExportService : any;

    constructor(
        private databaseService: DatabaseService
    ) {
        this.importExportService = (window as any).importExportService;
    }

    createOptions(format: string, remoteSync: boolean)
    {
        return this.importExportService.createOptions(format, remoteSync);
    }

    databaseImportText(options, text)
    {
        var database = this.databaseService.getDatabase();
        return this.importExportService.databaseImportText(database, options, text);
    }

    databaseImportFile(options, path)
    {
        var database = this.databaseService.getDatabase();
        return this.importExportService.databaseImportFile(database, options, path);
    }

    databaseExportText(options)
    {
        var database = this.databaseService.getDatabase();
        return this.importExportService.databaseExportText(database, options);
    }

    databaseExportFile(options, path)
    {
        var database = this.databaseService.getDatabase();
        return this.importExportService.databaseExportFile(database, options, path);
    }

}
