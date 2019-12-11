/*
 *  Copyright 2019 Mikhail Karmazin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rxhelpers;

import org.junit.Test;

public class Demo {

    @Test
    public void mainUseCaseDemo() {
        
        ReplayRemoveSubject<Integer> subject = ReplayRemoveSubject.create();

        subject.onNext(1);
        subject.onNext(2);
        subject.onNext(3);
        subject.subscribe(x -> System.out.println("SubscrA: " + x));
        System.out.println("(I) ----");
        // So far it's the same as ReplaySubject: output above (I) is 1, 2, 3

        subject.onRemove(2); // onRemove() is a new method
        subject.subscribe(x -> System.out.println("SubscrB: " + x));
        System.out.println("(II) ----");
        // output between (I) and (II):   1, 3   (all from SubscrB)

        subject.onNext(4);
        // output after (II):   4, 4   (one from SubscrA, other from SubscrB)
    }


    @Test
    public void updatesDemo() {

        ReplayRemoveSubject<Integer> subject = ReplayRemoveSubject.create();

        subject.subscribe(x -> System.out.println("SubscrA: " + x));
        subject.onNext(1);
        subject.onNext(1); // update
        subject.onNext(2);
        // output: 1, 1, 2

        System.out.println("(I) ----");
        subject.onRemove(2);
        subject.subscribe(x -> System.out.println("SubscrB: " + x)); // output: 1

        System.out.println("(II) ----");
        subject.onNext(2); // output: 2, 2

        System.out.println("III ----");
        subject.subscribe(x -> System.out.println("SubscrC: " + x)); // output: 1, 2
    }
}
