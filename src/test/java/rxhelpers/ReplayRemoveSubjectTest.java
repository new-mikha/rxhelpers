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

import io.reactivex.Observer;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests that are similar to those of the original ReplaySubject are stored in {@link ReplayRemoveSubjectCopiedTest}.
 * <p>
 * This file contains tests specific to the Remove functionality of {@link ReplayRemoveSubject}.
 */
public class ReplayRemoveSubjectTest {

    /**
     * Same as {@link Demo#mainUseCaseDemo()}, but now let's use asserts to make it a proper unit test
     */
    @Test
    public void mainUseCaseWithAsserts() {
        ReplayRemoveSubject<String> subject = ReplayRemoveSubject.create();

        { // Record "one", "two", "three":
            Observer<String> observer = TestHelper.mockObserver();
            subject.subscribe(observer);
            subject.onNext("one");
            subject.onNext("two");
            subject.onNext("three");

            verify(observer).onSubscribe(notNull());
            verify(observer, times(1)).onNext("one");
            verify(observer, times(1)).onNext("two");
            verify(observer, times(1)).onNext("three");
            verifyNoMoreInteractions(observer);
        }

        // Remove "two":
        subject.onRemove("two");

        { // Now only "one" and "three" are replayed:
            Observer<String> observer = TestHelper.mockObserver();
            subject.subscribe(observer);
            verify(observer).onSubscribe(notNull());
            verify(observer, times(1)).onNext("one");
            verify(observer, times(1)).onNext("three");
            verifyNoMoreInteractions(observer);
        }

    }


    /**
     * Same as {@link Demo#updatesDemo()}, but now let's use asserts to make it a proper unit test
     */
    @Test
    public void updatesWithAsserts() {

        ReplayRemoveSubject<Integer> subject = ReplayRemoveSubject.create();

        // Record 1, 2, 3
        Observer<Integer> observer1 = TestHelper.mockObserver();
        subject.subscribe(observer1);
        subject.onNext(1);
        subject.onNext(2);
        subject.onNext(2);

        verify(observer1).onSubscribe(notNull());
        verify(observer1, times(1)).onNext(1);
        verify(observer1, times(2)).onNext(2);
        verifyNoMoreInteractions(observer1);


        subject.onRemove(2);

        // Receive just 1
        Observer<Integer> observer2 = TestHelper.mockObserver();
        subject.subscribe(observer2);

        verify(observer2).onSubscribe(notNull());
        verify(observer2, times(1)).onNext(1);
        verifyNoMoreInteractions(observer2);

        subject.onNext(2);
        verify(observer1, times(3)).onNext(2);
        verify(observer2, times(1)).onNext(2);
        verifyNoMoreInteractions(observer1);
        verifyNoMoreInteractions(observer2);


        Observer<Integer> observer3 = TestHelper.mockObserver();
        subject.subscribe(observer3);

        verify(observer3).onSubscribe(notNull());
        verify(observer3, times(1)).onNext(1);
        verify(observer3, times(1)).onNext(2);
        verifyNoMoreInteractions(observer3);


    }
}
