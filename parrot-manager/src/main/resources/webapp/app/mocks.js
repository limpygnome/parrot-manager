/*
    Simple JS file for mocking injected dependencies during Parrot runtime.
*/

if (window.settingsService == null)
{
    window.settingsService = (function(){
        return {
            getSettings : function() {
                return {
                    getRecentFilesEnabled : function() { return { getValue : function() { return "true" } } },
                    getRecentFilesOpenLastOnStartup : function() { return { getValue : function() { return "true" } } },
                    getAutomaticBackupsOnSave : function() { return { getValue : function() { return "true" } } },
                    getAutomaticBackupsRetained : function() { return { getValue : function() { return "20" } } }
                }
            }
        }
    })();
}


if (window.runtimeService == null)
{
    window.runtimeService = (function(){
        return {
            exit: function() { alert("tried to exit"); },
            changeHeight: function() { },
            pickFile: function() { return "test.parrot" },
            isDevelopmentMode: function() { return true; },
            refreshPage: function() { location.reload(); }
        }
    })();
}


if (window.randomGeneratorService == null)
{
    window.randomGeneratorService = (function(){

        return {

            generate : function() { return "random string" }

        }

    })();
}


if (window.databaseService == null)
{
    window.databaseService = (function(){

        return {
            isOpen: function() { return true; },
            isDirty: function() { return true; },
            getFileName: function() { return "test.parrot"; },
            create: function() { return true; },
            open: function() { return "test error"; },
            save: function() { return "test save error"; },
            close: function() { },
            getDatabase: function()
            {
                return {
                    getRoot : function() {
                        return {
                            getId : function() { return "d1b07836-83d0-486e-b329-4c41d317295d" },
                            getName : function() { return "hello" },
                            getChildren : function() { return [

                                {
                                    getId : function() { return "a1b07836-83d0-486e-b329-4c41d317295d" },
                                    getName : function() { return "child 1" },
                                    getChildren : function() { return [] }
                                },

                                {
                                    getId : function() { return "66aaf8d3-05fc-4bc7-afe5-d04e2cbaa26c" },
                                    getName : function() { return "child 2" },
                                    getChildren : function() { return [] }
                                },

                                {
                                    getId : function() { return "97245ef0-d33c-4b7c-8f2f-dba4641f713c" },
                                    getName : function() { return "child 3" },
                                    getChildren : function() { return [] }
                                }

                            ] },
                            isRoot : function() { return false }
                        }
                    },

                    getNode: function(nodeId) {
                        return {
                           getId : function() { return "d1b07836-83d0-486e-b329-4c41d317295d" },
                           getName : function() { return "(unnamed)" },
                           getLastModified: function() { return 1484723619 },
                           getFormattedLastModified: function() { return "XX-XX-XXXX XX:XX:XX" },
                           getDecryptedValueString: function() { return "mock decrypted value\ntest\nline3\nline4\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na\na" },
                           getChildCount: function() { return 2 },
                           getChildren : function() { return [

                                {
                                    getId : function() { return "a1b07836-83d0-486e-b329-4c41d317295d" },
                                    getName : function() { return "child node" }
                                },
                                {
                                    getId : function() { return "b1b07836-83d0-486e-b329-4c41d317295d" },
                                    getName : function() { return "child node 2" }
                                }

                           ] },
                           isRoot : function() { return false },
                           setValueString : function() {}
                       }
                    }
                }
            }
        };

    })();

}


if (window.recentFileService == null)
{
    window.recentFileService = (function(){

        return {

           fetch : function() { return []; },
           clear : function() { }

        }

    })();
}


if (window.encryptedValueService == null)
{
    window.encryptedValueService = (function(){

        return {

           asString : function() { return "decrypted value test" },
           fromString : function() { }

        }

    })();
}

if (window.backupService == null)
{
    window.backupService = (function(){

        return {

           create : function() { return true }

        }

    })();
}

if (window.buildInfoService == null)
{
    window.buildInfoService = (function(){

        return {

           getBuildInfo : function() { return "fake build info" }

        }

    })();
}
