import { Component, Renderer, Input, Output, EventEmitter } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';

import { ViewerService } from 'app/service/ui/viewer.service'
import { DatabaseService } from 'app/service/database.service'
import { RandomGeneratorService } from 'app/service/randomGenerator.service'
import { EncryptedValueService } from 'app/service/encryptedValue.service'

import { DatabaseNode } from "app/model/databaseNode"

@Component({
    selector: 'generate-random',
    templateUrl: 'generate-random.component.html',
    providers: [RandomGeneratorService],
})
export class GenerateRandomComponent
{

    // The current node being changed; passed from parent
    @Input() currentNode : DatabaseNode;

    // Form of options for random value generator
    public randomOptions : any;

    constructor(
        private viewerService: ViewerService,
        private databaseService: DatabaseService,
        private randomGeneratorService: RandomGeneratorService,
        private encryptedValueService: EncryptedValueService,
        private renderer: Renderer,
        public fb: FormBuilder
    ) {
        // Set default values
        this.randomOptions = this.fb.group({
             useNumbers: ["true"],
             useUppercase: ["true"],
             useLowercase: ["true"],
             useSpecialChars: ["true"],
             minLength: ["10"],
             maxLength: ["18"]
        });
    }

    generate()
    {
        // Retrieve form data
        var form = this.randomOptions;

        var useNumbers = form.value["useNumbers"] == "true";
        var useUppercase = form.value["useUppercase"] == "true";
        var useLowercase = form.value["useLowercase"] == "true";
        var useSpecialChars = form.value["useSpecialChars"] == "true";
        var minLength = form.value["minLength"];
        var maxLength = form.value["maxLength"];

        console.log("generating random password... - numbers: " + useNumbers + ", upper: " + useUppercase + ", lower: " +
                        useLowercase + ", special: " + useSpecialChars + ", min: " + minLength + ", max: " + maxLength);

        // Generate value
        var randomPassword = this.randomGeneratorService.generate(useNumbers, useUppercase, useLowercase, useSpecialChars, minLength, maxLength);

        if (randomPassword != null)
        {
            // Update value of current node
            this.encryptedValueService.setString(this.currentNode.id, randomPassword);
            console.log("current password updated with random string - node id: " + this.currentNode.id);

            // Show notification
            toastr.info("Updated with random value");

            // Mark entire viewer as invalid
            this.viewerService.changed();
        }
        else
        {
            console.log("random generator service returned null, check length is valid and there's at least one group of chars selected");
        }
    }

}
