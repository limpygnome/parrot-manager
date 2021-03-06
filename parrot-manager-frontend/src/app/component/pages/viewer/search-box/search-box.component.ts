import { Component, Output, EventEmitter } from '@angular/core';

@Component({
    selector: 'search-box',
    styleUrls: ['search-box.component.css'],
    templateUrl: 'search-box.component.html'
})
export class SearchBoxComponent
{
    @Output() filter = new EventEmitter();

    private delayHandle: any;

    constructor() { }

    ngOnInit()
    {
        $("#search").on("change keydown", () => {
            this.prepareUpdate();
        });
    }

    prepareUpdate()
    {
        if (this.delayHandle != null)
        {
            clearTimeout(this.delayHandle);
        }

        this.delayHandle = setTimeout(() => {
            this.update();
        }, 100);
    }

    update()
    {
        var value = $("#search").val();
        this.filter.emit(value);
    }

    reset()
    {
        $("#search").val("");
        this.update();
    }

    isResetVisible()
    {
        var value = $("#search").val();
        return value != null && value.length > 0;
    }

}
