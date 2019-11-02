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

import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.Subject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ReplayRemoveSubjectConcurrencyTest {

    @Ignore
    @Test
    public void speedTest() throws InterruptedException {
        // Need to repeat a few times since some stuff (e.g. UnicastSubject) need some additional time when created
        // for the first time. Plus it seems JVM needs to settle, and after a few iterations results are more stable:
        for (int i = 0; i < 10; i++) {
            singleSpeedTest();
        }
    }


    private void singleSpeedTest() throws InterruptedException {

        final Subject<Long> replay = ReplayRemoveSubject.create();

        final Date startTime = new Date();

        Thread source = new Thread(() -> {
//        Observable.unsafeCreate((ObservableSource<Long>) o -> {
//          o.onSubscribe(Disposables.empty());
//          //System.out.println("********* Start Source Data ***********");
            final Date addStartTime = new Date();

            final long startMs =
                addStartTime.getTime() - startTime.getTime();

            for (long l = 1; l <= 1000000; l++) {
                replay.onNext(l);
            }
            replay.onComplete();
            final Date finishTime = new Date();
            final long msPassed =
                finishTime.getTime() - addStartTime.getTime();
            System.out.println(
                String.format("********* Finished Adding Source Data, started at %d ms, took %d ms ***********",
                    startMs, msPassed
                ));
//        })
//            .subscribe(replay)
        });


        source.start();
        source.join();

        final Date subscrStart = new Date();

        AtomicInteger counter = new AtomicInteger();

        //noinspection ResultOfMethodCallIgnored
        replay
            .subscribe(
                value -> counter.incrementAndGet(),
                err -> {
                },
                () -> {
                    final Date finishTime = new Date();
                    final long msPassed =
                        finishTime.getTime() - subscrStart.getTime();

                    long startMs = subscrStart.getTime() - startTime.getTime();
                    System.out.println(
                        String.format("********* Finished overall for %d values, subscribed at %d ms, took %d ms ***********",
                            counter.get(), startMs, msPassed
                        ));
                    //System.out.println(String.format("Took %d ms", msPassed));
                }
            );

        Thread.sleep(1000);

        System.out.println("Done");
    }


    @Test
    public void testReplayRemoveSubjectConcurrentObservations() throws InterruptedException {

        /*
        The test observes the source on multiple threads.
        The source at some point starts removal of the previously added elements.
        A race conditions imitated, when removal goes on while some threads already took the removed values,
        some did not took all of the removed values (and hence should not see all the removed ones), and some threads
        take some removed values but not the others.
         */

        final ReplayRemoveSubject<Long> replay = ReplayRemoveSubject.create();

        final int THREAD_COUNT = 20;
        final int START_SOURCE_AFTER_THREADS = 3;
        final int THREADS_TO_RECEIVE_ALL_BEFORE_REMOVE_STARTED = 5;
        final int AWAITER_1_THREAD_COUNT = 10;
        final int AWAITER_2_THREAD_COUNT = 12;
        final int AWAITER_3_THREAD_COUNT = 14;

        final CountDownLatch sourceAwaiterMain = new CountDownLatch(THREADS_TO_RECEIVE_ALL_BEFORE_REMOVE_STARTED);

        final CountDownLatch threadCreationAwaiter1 = new CountDownLatch(1);
        final CountDownLatch sourceAwaiter1 = new CountDownLatch(3);

        final CountDownLatch threadCreationAwaiter2 = new CountDownLatch(1);
        final CountDownLatch sourceAwaiter2 = new CountDownLatch(3);

        final CountDownLatch threadCreationAwaiter3 = new CountDownLatch(1);

        final List<Long> valuesToRemove = new ArrayList<>();
        valuesToRemove.add(1L);
        valuesToRemove.add(20L);
        valuesToRemove.add(300L);

        AtomicLong fullCounter = new AtomicLong();

        Thread source = new Thread(() -> {
            try {
                System.out.println("********* Start Source Data ***********");
                for (long l = 1; l <= 10000; l++) {
                    if (l % 100 == 0)
                        System.out.println("Emitting " + l);

                    fullCounter.addAndGet(l);
                    replay.onNext(l);

                    // just random value, could be any:
                    if (l == 567) {
                        sourceAwaiterMain.await();

                        replay.onRemove(valuesToRemove.get(0));
                        threadCreationAwaiter1.countDown();
                        sourceAwaiter1.await();

                        replay.onRemove(valuesToRemove.get(1));
                        threadCreationAwaiter2.countDown();
                        sourceAwaiter2.await();

                        replay.onRemove(valuesToRemove.get(2));
                        threadCreationAwaiter3.countDown();
                    }

                }
                System.out.println("********* Finished Source Data ***********");
                replay.onComplete();

            } catch (Exception e) {
                fail(e.getMessage());
            }
        });

        // used to collect results of each thread
        final List<Pair<Long, List<Long>>> listOfListsOfValues = Collections.synchronizedList(new ArrayList<>());


        CountDownLatch countDownLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 1; i <= THREAD_COUNT; i++) {

            final int count = i;

            if (count == START_SOURCE_AFTER_THREADS) {
                // start source data after we have some already subscribed
                // and while others are in process of subscribing
                source.start();
            }

            if (count == AWAITER_1_THREAD_COUNT)
                threadCreationAwaiter1.await();

            if (count == AWAITER_2_THREAD_COUNT)
                threadCreationAwaiter2.await();

            if (count == AWAITER_3_THREAD_COUNT)
                threadCreationAwaiter3.await();

            try {
                AtomicLong phase = new AtomicLong();

                //noinspection ResultOfMethodCallIgnored
                replay
                    .observeOn(Schedulers.newThread())
                    .doOnNext(val -> {
                        final int indexOfRemovable =
                            valuesToRemove.indexOf(val);

                        if (indexOfRemovable >= 0) {
                            phase.addAndGet(val);
                        }

                        if (val.equals(300L))
                            sourceAwaiterMain.countDown();

                        if (count >= 10)
                            sourceAwaiter1.countDown();

                        if (count >= 12)
                            sourceAwaiter2.countDown();


                    })
                    .toList()
                    .subscribe(values -> {
                        listOfListsOfValues.add(Pair.of(phase.get(), values));
                        countDownLatch.countDown();
                        System.out.println("Finished thread: " + count);
                    });
            } catch (Exception e) {
                fail(e.getMessage());
            }

            System.out.println("Started thread: " + i);
        }

        countDownLatch.await();

        listOfListsOfValues.stream().map(Pair::getLeft).collect(Collectors.toSet()).
            forEach(System.out::println);

        long maxRemoved = valuesToRemove.stream().reduce(0L, Long::sum);

        // assert all threads got the same results
        Map<Long, Long> sums = new HashMap<>();
        for (Pair<Long, List<Long>> pair : listOfListsOfValues) {
            long v = 0;
            for (long l : pair.getRight()) {
                v += l;
            }

            long phase = pair.getKey();
            assertEquals(fullCounter.get() - (maxRemoved - phase), v);

            sums.put(pair.getLeft(), v);
        }

        assertEquals(valuesToRemove.size() + 1, sums.size());
        Long phaseAcc = 0L;
        for (int i = valuesToRemove.size() - 1; i >= 0; i--) {
            assertTrue(sums.containsKey(phaseAcc));
            phaseAcc += valuesToRemove.get(i);
            assertTrue(sums.containsKey(phaseAcc));
        }

    }

}
