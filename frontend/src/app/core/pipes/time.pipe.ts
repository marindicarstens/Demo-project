import { Pipe, PipeTransform } from '@angular/core';

/** An API LocalTime ("09:00:00") as the customer sees it ("09:00"). */
@Pipe({ name: 'time' })
export class TimePipe implements PipeTransform {
  transform(time: string): string {
    return time.slice(0, 5);
  }
}
