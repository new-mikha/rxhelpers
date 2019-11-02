/*
 * This file is pretty much a copy (with minor changes stated below) of the original RX's ReplaySubjectConcurrencyTest.
 * So including the original license text, as it's required by it:
 *
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package rxhelpers;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import io.reactivex.subjects.Subject;
import org.junit.*;
import org.mockito.*;

import io.reactivex.*;
import io.reactivex.disposables.*;
import io.reactivex.functions.*;
import io.reactivex.observers.*;
import io.reactivex.schedulers.*;

/**
 * This is pretty much a copy (with minor changes) of the original RX's ReplaySubjectTest.
 * For tests specific to the {@link ReplayRemoveSubject}, see {@link ReplayRemoveSubjectTest}.
 *
 * Tests related to limiting the buffer by time/size are removed since ReplayRemoveSubject doesn't support
 * this, and some minor additions are made. ReplayRemoveSubject obviously needs to support the rest, cause without
 * the Remove part it's pretty much the same as the original one.
 */
public class ReplayRemoveSubjectCopiedTest extends SubjectTest<Integer> {

    private final Throwable testException = new Throwable();

    @Override
    protected Subject<Integer> create() {
        return ReplayRemoveSubject.create();
    }

    @Test
    public void testCompleted() {
        ReplayRemoveSubject<String> subject = ReplayRemoveSubject.create();

        Observer<String> o1 = TestHelper.mockObserver();
        subject.subscribe(o1);

        subject.onNext("one");
        subject.onNext("two");
        subject.onNext("three");
        subject.onComplete();

        subject.onNext("four");
        subject.onComplete();
        subject.onError(new Throwable());

        assertCompletedSubscriber(o1);

        // assert that subscribing a 2nd time gets the same data
        Observer<String> o2 = TestHelper.mockObserver();
        subject.subscribe(o2);
        assertCompletedSubscriber(o2);
    }

    @Test
    public void testCompletedStopsEmittingData() {
        ReplayRemoveSubject<Integer> channel = ReplayRemoveSubject.create();
        Observer<Object> observerA = TestHelper.mockObserver();
        Observer<Object> observerB = TestHelper.mockObserver();
        Observer<Object> observerC = TestHelper.mockObserver();
        Observer<Object> observerD = TestHelper.mockObserver();
        TestObserver<Object> to = new TestObserver<>(observerA);

        channel.subscribe(to);
        channel.subscribe(observerB);

        InOrder inOrderA = inOrder(observerA);
        InOrder inOrderB = inOrder(observerB);
        InOrder inOrderC = inOrder(observerC);
        InOrder inOrderD = inOrder(observerD);

        channel.onNext(42);

        // both A and B should have received 42 from before subscription
        inOrderA.verify(observerA).onNext(42);
        inOrderB.verify(observerB).onNext(42);

        to.dispose();

        // a should receive no more
        inOrderA.verifyNoMoreInteractions();

        channel.onNext(4711);

        // only be should receive 4711 at this point
        inOrderB.verify(observerB).onNext(4711);

        channel.onComplete();

        // B is subscribed so should receive onComplete
        inOrderB.verify(observerB).onComplete();

        channel.subscribe(observerC);

        // when C subscribes it should receive 42, 4711, onComplete
        inOrderC.verify(observerC).onNext(42);
        inOrderC.verify(observerC).onNext(4711);
        inOrderC.verify(observerC).onComplete();

        // if further events are propagated they should be ignored
        channel.onNext(13);
        channel.onNext(14);
        channel.onNext(15);
        channel.onError(new RuntimeException());

        // a new subscription should only receive what was emitted prior to terminal state onComplete
        channel.subscribe(observerD);

        inOrderD.verify(observerD).onNext(42);
        inOrderD.verify(observerD).onNext(4711);
        inOrderD.verify(observerD).onComplete();

        verify(observerA).onSubscribe(notNull());
        verify(observerB).onSubscribe(notNull());
        verify(observerC).onSubscribe(notNull());
        verify(observerD).onSubscribe(notNull());
        Mockito.verifyNoMoreInteractions(observerA);
        Mockito.verifyNoMoreInteractions(observerB);
        Mockito.verifyNoMoreInteractions(observerC);
        Mockito.verifyNoMoreInteractions(observerD);

    }

    @Test
    public void testCompletedAfterError() {
        ReplayRemoveSubject<String> subject = ReplayRemoveSubject.create();

        {
            Observer<String> observer = TestHelper.mockObserver();

            subject.onNext("one");
            subject.onError(testException);
            subject.onNext("two");
            subject.onComplete();
            subject.onError(new RuntimeException());

            subject.subscribe(observer);
            verify(observer).onSubscribe(notNull());
            verify(observer, times(1)).onNext("one");
            verify(observer, times(1)).onError(testException);
            verifyNoMoreInteractions(observer);
        }

        {
            Observer<String> observer = TestHelper.mockObserver();

            subject.subscribe(observer);
            verify(observer).onSubscribe(notNull());
            verify(observer, times(1)).onNext("one");
            verify(observer, times(1)).onError(testException);
            verifyNoMoreInteractions(observer);
        }


    }

    private void assertCompletedSubscriber(Observer<String> observer) {
        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, Mockito.never()).onError(any(Throwable.class));
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testError() {
        ReplayRemoveSubject<String> subject = ReplayRemoveSubject.create();

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.onNext("one");
        subject.onNext("two");
        subject.onNext("three");
        subject.onError(testException);

        subject.onNext("four");
        subject.onError(new Throwable());
        subject.onComplete();

        assertErrorSubscriber(observer);

        observer = TestHelper.mockObserver();
        subject.subscribe(observer);
        assertErrorSubscriber(observer);
    }

    private void assertErrorSubscriber(Observer<String> observer) {
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, times(1)).onError(testException);
        verify(observer, Mockito.never()).onComplete();
    }

    @Test
    public void testSubscribeMidSequence() {
        ReplayRemoveSubject<String> subject = ReplayRemoveSubject.create();

        Observer<String> observer = TestHelper.mockObserver();
        subject.subscribe(observer);

        subject.onNext("one");
        subject.onNext("two");

        assertObservedUntilTwo(observer);

        Observer<String> anotherSubscriber = TestHelper.mockObserver();
        subject.subscribe(anotherSubscriber);
        assertObservedUntilTwo(anotherSubscriber);

        subject.onNext("three");
        subject.onComplete();

        assertCompletedSubscriber(observer);
        assertCompletedSubscriber(anotherSubscriber);
    }

    @Test
    public void testUnsubscribeFirstSubscriber() {
        ReplayRemoveSubject<String> subject = ReplayRemoveSubject.create();

        Observer<String> observer = TestHelper.mockObserver();
        TestObserver<String> to = new TestObserver<>(observer);
        subject.subscribe(to);

        subject.onNext("one");
        subject.onNext("two");

        to.dispose();
        assertObservedUntilTwo(observer);

        Observer<String> anotherSubscriber = TestHelper.mockObserver();
        subject.subscribe(anotherSubscriber);
        assertObservedUntilTwo(anotherSubscriber);

        subject.onNext("three");
        subject.onComplete();

        assertObservedUntilTwo(observer);
        assertCompletedSubscriber(anotherSubscriber);
    }

    private void assertObservedUntilTwo(Observer<String> observer) {
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, Mockito.never()).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, Mockito.never()).onComplete();
    }

    @Test(timeout = 2000)
    public void testNewSubscriberDoesntBlockExisting() throws InterruptedException {

        final AtomicReference<String> lastValueForSubscriber1 = new AtomicReference<>();
        Observer<String> observer1 = new DefaultObserver<String>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(String v) {
                System.out.println("observer1: " + v);
                lastValueForSubscriber1.set(v);
            }

        };

        final AtomicReference<String> lastValueForSubscriber2 = new AtomicReference<>();
        final CountDownLatch oneReceived = new CountDownLatch(1);
        final CountDownLatch makeSlow = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        Observer<String> observer2 = new DefaultObserver<String>() {

            @Override
            public void onComplete() {
                completed.countDown();
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(String v) {
                System.out.println("observer2: " + v);
                if (v.equals("one")) {
                    oneReceived.countDown();
                } else {
                    try {
                        makeSlow.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    lastValueForSubscriber2.set(v);
                }
            }

        };

        ReplayRemoveSubject<String> subject = ReplayRemoveSubject.create();
        subject.subscribe(observer1);
        subject.onNext("one");
        assertEquals("one", lastValueForSubscriber1.get());
        subject.onNext("two");
        assertEquals("two", lastValueForSubscriber1.get());

        // use subscribeOn to make this async otherwise we deadlock as we are using CountDownLatches
        subject.subscribeOn(Schedulers.newThread()).subscribe(observer2);

        System.out.println("before waiting for one");

        // wait until observer2 starts having replay occur
        oneReceived.await();

        System.out.println("after waiting for one");

        subject.onNext("three");

        System.out.println("sent three");

        // if subscription blocked existing subscribers then 'makeSlow' would cause this to not be there yet
        assertEquals("three", lastValueForSubscriber1.get());

        System.out.println("about to send onComplete");

        subject.onComplete();

        System.out.println("completed subject");

        // release
        makeSlow.countDown();

        System.out.println("makeSlow released");

        completed.await();
        // all of them should be emitted with the last being "three"
        assertEquals("three", lastValueForSubscriber2.get());

    }

    @Test
    public void testSubscriptionLeak() {
       /*
       TODO mk
       DistinctReplayRemoveSubject<Object> subject = DistinctReplayRemoveSubject.create();

        Disposable d = subject.subscribe();

        assertEquals(1, subject.observerCount());

        d.dispose();

        assertEquals(0, subject.observerCount());*/
    }

    @Test(timeout = 1000)
    public void testUnsubscriptionCase() {
        ReplayRemoveSubject<String> src = ReplayRemoveSubject.create();

        for (int i = 0; i < 10; i++) {
            final Observer<Object> o = TestHelper.mockObserver();
            InOrder inOrder = inOrder(o);
            String v = "" + i;
            src.onNext(v);
            System.out.printf("Turn: %d%n", i);
            src.firstElement()
                    .toObservable()
                    .flatMap((Function<String, Observable<String>>) t1 -> Observable.just(t1 + ", " + t1))
                    .subscribe(new DefaultObserver<String>() {
                        @Override
                        public void onNext(String t) {
                            System.out.println(t);
                            o.onNext(t);
                        }

                        @Override
                        public void onError(Throwable e) {
                            o.onError(e);
                        }

                        @Override
                        public void onComplete() {
                            o.onComplete();
                        }
                    });
            inOrder.verify(o).onNext("0, 0");
            inOrder.verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }

    @Test
    public void testTerminateOnce() {
        ReplayRemoveSubject<Integer> source = ReplayRemoveSubject.create();
        source.onNext(1);
        source.onNext(2);
        source.onComplete();

        final Observer<Integer> o = TestHelper.mockObserver();

        source.subscribe(new DefaultObserver<Integer>() {

            @Override
            public void onNext(Integer t) {
                o.onNext(t);
            }

            @Override
            public void onError(Throwable e) {
                o.onError(e);
            }

            @Override
            public void onComplete() {
                o.onComplete();
            }
        });

        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    // FIXME RS subscribers can't throw
//    @Test
//    public void testOnErrorThrowsDoesntPreventDelivery() {
//        DistinctReplayRemoveSubject<String> ps = DistinctReplayRemoveSubject.create();
//
//        ps.subscribe();
//        TestObserver<String> to = new TestObserver<String>();
//        ps.subscribe(to);
//
//        try {
//            ps.onError(new RuntimeException("an exception"));
//            fail("expect OnErrorNotImplementedException");
//        } catch (OnErrorNotImplementedException e) {
//            // ignore
//        }
//        // even though the onError above throws we should still receive it on the other subscriber
//        assertEquals(1, to.errors().size());
//    }

    // FIXME RS subscribers can't throw
//    /**
//     * This one has multiple failures so should get a CompositeException
//     */
//    @Test
//    public void testOnErrorThrowsDoesntPreventDelivery2() {
//        DistinctReplayRemoveSubject<String> ps = DistinctReplayRemoveSubject.create();
//
//        ps.subscribe();
//        ps.subscribe();
//        TestObserver<String> to = new TestObserver<String>();
//        ps.subscribe(to);
//        ps.subscribe();
//        ps.subscribe();
//        ps.subscribe();
//
//        try {
//            ps.onError(new RuntimeException("an exception"));
//            fail("expect OnErrorNotImplementedException");
//        } catch (CompositeException e) {
//            // we should have 5 of them
//            assertEquals(5, e.getExceptions().size());
//        }
//        // even though the onError above throws we should still receive it on the other subscriber
//        assertEquals(1, to.getOnErrorEvents().size());
//    }

    @Test
    public void testCurrentStateMethodsNormal() {
        ReplayRemoveSubject<Object> as = ReplayRemoveSubject.create();

        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());

        as.onNext(1);

        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());

        as.onComplete();

        assertFalse(as.hasThrowable());
        assertTrue(as.hasComplete());
        assertNull(as.getThrowable());
    }

    @Test
    public void testCurrentStateMethodsEmpty() {
        ReplayRemoveSubject<Object> as = ReplayRemoveSubject.create();

        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());

        as.onComplete();

        assertFalse(as.hasThrowable());
        assertTrue(as.hasComplete());
        assertNull(as.getThrowable());
    }

    @Test
    public void testCurrentStateMethodsError() {
        ReplayRemoveSubject<Object> as = ReplayRemoveSubject.create();

        assertFalse(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertNull(as.getThrowable());

        as.onError(new TestException());

        assertTrue(as.hasThrowable());
        assertFalse(as.hasComplete());
        assertTrue(as.getThrowable() instanceof TestException);
    }

    @Test
    public void testSizeAndHasAnyValueUnbounded() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());

        rs.onNext(1);

        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());

        rs.onNext(2);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());

        rs.onComplete();

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }

    @Test
    public void testSizeAndHasAnyValueEffectivelyUnbounded() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());

        rs.onNext(1);

        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());

        rs.onNext(2);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());

        rs.onComplete();

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }

    @Test
    public void testSizeAndHasAnyValueUnboundedError() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());

        rs.onNext(1);

        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());

        rs.onNext(2);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());

        rs.onError(new TestException());

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }

    @Test
    public void testSizeAndHasAnyValueEffectivelyUnboundedError() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());

        rs.onNext(1);

        assertEquals(1, rs.size());
        assertTrue(rs.hasValue());

        rs.onNext(2);

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());

        rs.onError(new TestException());

        assertEquals(2, rs.size());
        assertTrue(rs.hasValue());
    }

    @Test
    public void testSizeAndHasAnyValueUnboundedEmptyError() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        rs.onError(new TestException());

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }

    @Test
    public void testSizeAndHasAnyValueEffectivelyUnboundedEmptyError() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        rs.onError(new TestException());

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }

    @Test
    public void testSizeAndHasAnyValueUnboundedEmptyCompleted() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        rs.onComplete();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }

    @Test
    public void testSizeAndHasAnyValueEffectivelyUnboundedEmptyCompleted() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();

        rs.onComplete();

        assertEquals(0, rs.size());
        assertFalse(rs.hasValue());
    }

    @Test
    public void testGetValues() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();
        Object[] expected = new Object[10];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = i;
            rs.onNext(i);
            assertArrayEquals(Arrays.copyOf(expected, i + 1), rs.getValues());
        }
        rs.onComplete();

        assertArrayEquals(expected, rs.getValues());

    }

    @Test
    public void testGetValuesUnbounded() {
        ReplayRemoveSubject<Object> rs = ReplayRemoveSubject.create();
        Object[] expected = new Object[10];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = i;
            rs.onNext(i);
            assertArrayEquals(Arrays.copyOf(expected, i + 1), rs.getValues());
        }
        rs.onComplete();

        assertArrayEquals(expected, rs.getValues());

    }

    @Test
    public void hasSubscribers() {
        ReplayRemoveSubject<Integer> rp = ReplayRemoveSubject.create();

        assertFalse(rp.hasObservers());

        TestObserver<Integer> to = rp.test();

        assertTrue(rp.hasObservers());

        to.cancel();

        assertFalse(rp.hasObservers());
    }

    @Test
    public void peekStateUnbounded() {
        ReplayRemoveSubject<Integer> rp = ReplayRemoveSubject.create();

        rp.onNext(1);

        assertEquals((Integer) 1, rp.getValue());

        assertEquals(1, rp.getValues()[0]);
    }

    @Test
    public void subscribeCancelRace() {
        for (int i = 0; i < TestHelper.RACE_DEFAULT_LOOPS; i++) {
            final TestObserver<Integer> to = new TestObserver<>();

            final ReplayRemoveSubject<Integer> rp = ReplayRemoveSubject.create();

            Runnable r1 = () -> rp.subscribe(to);

            Runnable r2 = to::cancel;

            TestHelper.race(r1, r2);
        }
    }

    @Test
    public void subscribeAfterDone() {
        ReplayRemoveSubject<Integer> rp = ReplayRemoveSubject.create();
        rp.onComplete();

        Disposable bs = Disposables.empty();

        rp.onSubscribe(bs);

        assertTrue(bs.isDisposed());
    }

    @Test
    public void subscribeRace() {
        for (int i = 0; i < TestHelper.RACE_DEFAULT_LOOPS; i++) {
            final ReplayRemoveSubject<Integer> rp = ReplayRemoveSubject.create();

            //noinspection ResultOfMethodCallIgnored
            Runnable r1 = rp::test;

            TestHelper.race(r1, r1);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void cancelUpfront() {
        ReplayRemoveSubject<Integer> rp = ReplayRemoveSubject.create();
        rp.test();
        rp.test();

        TestObserver<Integer> to = rp.test(true);

        assertEquals(2, rp.observerCount());

        to.assertEmpty();
    }

    @Test
    public void cancelRace() {
        for (int i = 0; i < TestHelper.RACE_DEFAULT_LOOPS; i++) {

            final ReplayRemoveSubject<Integer> rp = ReplayRemoveSubject.create();
            final TestObserver<Integer> to1 = rp.test();
            final TestObserver<Integer> to2 = rp.test();

            Runnable r1 = to1::cancel;

            Runnable r2 = to2::cancel;

            TestHelper.race(r1, r2);

            assertFalse(rp.hasObservers());
        }
    }

    @Test
    public void dispose() {
        TestHelper.checkDisposed(ReplayRemoveSubject.create());
    }
}