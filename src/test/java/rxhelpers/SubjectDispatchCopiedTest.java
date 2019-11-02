package rxhelpers;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.junit.Assert.assertTrue;

// This is a copy of this:
//      https://bitbucket.org/marshallpierce/rxjava-ReplayRemoveSubject-race/src/master/src/test/java/org/mpierce/rxjava/SubjectDispatchTest.java
// found there:
//      https://github.com/ReactiveX/RxJava/issues/1940
// The Remove functionality is not tested here - just checking that ReplayRemoveSubject is working for the scenarios
// where the ReplaySubject is.
//
@RunWith(Parameterized.class)
public final class SubjectDispatchCopiedTest {

    private static ExecutorService observeExecutor;

    private static ExecutorService subscribeExecutor;
    private static ExecutorService miscExecutor;

    private Scheduler observeScheduler;
    private Scheduler subscribeScheduler;
    private CountDownLatch latch;
    private ReplayRemoveSubject<String> subj;

    public SubjectDispatchCopiedTest(int ignored) {
    }

    @Parameters
    public static Collection<Object[]> data() {
        Integer[][] a = new Integer[10000][1];
        for (Integer[] integers : a) {
            Arrays.fill(integers, 0);
        }
        return Arrays.asList((Object[][]) a);
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        observeExecutor = newSingleThreadExecutor();
        subscribeExecutor = newSingleThreadExecutor();
        miscExecutor = newSingleThreadExecutor();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        observeExecutor.shutdownNow();
        subscribeExecutor.shutdownNow();
        miscExecutor.shutdownNow();
    }

    @Before
    public void setUp() throws Exception {
        observeScheduler = Schedulers.from(observeExecutor);
        subscribeScheduler = Schedulers.from(subscribeExecutor);
        latch = new CountDownLatch(1);
        subj = ReplayRemoveSubject.create();
    }

    @Test
    public void testCallsObserverWhenSubscribedFromMainThread() throws InterruptedException {
        submitRunnableThatWaitsThenCallsOnNext();

        SemaphoreObserver observer = new SemaphoreObserver();
        subj
                .observeOn(observeScheduler)
                .subscribe(observer);

        latch.countDown();

        assertTrue(observer.next.tryAcquire(2000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testCallsObserverWhenSubscribesFromSubscriberScheduler() throws InterruptedException {
        submitRunnableThatWaitsThenCallsOnNext();

        SemaphoreObserver observer = new SemaphoreObserver();
        subj
                .observeOn(observeScheduler)
                .subscribeOn(subscribeScheduler)
                .subscribe(observer);

        latch.countDown();

        assertTrue(observer.next.tryAcquire(2000, TimeUnit.MILLISECONDS));
    }

    private void submitRunnableThatWaitsThenCallsOnNext() {
        miscExecutor.submit(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            subj.onNext("foo");
        });
    }

    private static class SemaphoreObserver implements Observer<String> {

        final Semaphore next = new Semaphore(0);
        final Semaphore complete = new Semaphore(0);
        final Semaphore error = new Semaphore(0);

        @Override
        public void onComplete() {
            complete.release();
        }

        @Override
        public void onError(Throwable e) {
            error.release();
        }

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(String s) {
            next.release();
        }
    }
}
