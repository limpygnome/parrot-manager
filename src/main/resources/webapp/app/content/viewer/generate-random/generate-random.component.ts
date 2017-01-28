import { Component, Renderer, Input } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { DatabaseService } from 'app/service/database.service'
import { RandomGeneratorService } from 'app/service/randomGenerator.service'

@Component({
    moduleId: module.id,
    selector: 'generate-random',
    templateUrl: 'generate-random.component.html',
    providers: [DatabaseService, RandomGeneratorService],
})
export class GenerateRandomComponent
{

    // The current node being changed; passed from parent
    @Input() currentNode : any;

    // Form of options for random value generator
    public randomOptions : any;

    constructor(private databaseService: DatabaseService, private randomGeneratorService: RandomGeneratorService,
                private renderer: Renderer, public fb: FormBuilder)
    {
        // Set default values
        // TODO: read/save in database eventually
        this.randomOptions = this.fb.group({
             useNumbers: ["true"],
             useUppercase: ["true"],
             useLowercase: ["true"],
             useSpecialChars: ["true"],
             minLength: ["8"],
             maxLength: ["12"]
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
            this.currentNode.setValueString(randomPassword);

            console.log("current password updated with random string - node id: " + this.currentNode.getId());
        }
        else
        {
            console.log("random generator service returned null, check length is valid and there's at least one group of chars selected");
        }
    }

}
