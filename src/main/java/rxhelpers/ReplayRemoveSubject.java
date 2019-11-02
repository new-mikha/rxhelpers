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

import com.google.common.collect.Iterables;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.functions.ObjectHelper;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.ReplaySubject;
import io.reactivex.subjects.Subject;
import io.reactivex.subjects.UnicastSubject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The subject works like a {@link ReplaySubject}, i.e. it replays events to current and late {@link Observer}s.
 * <p>
 * However it also allows to remove an element via {@link #onRemove} method. When subscribed to, the subject provides
 * current distinct set of elements, and also relays all additions and updates, but not removals (a key selector can be
 * provided if needed, otherwise it compares elements by their equal() and hashCode() method, just like a HashMap does)
 * <p>
 * There is also a variety of method to observe either snapshot data, additions, updates, removals, or altogether. The
 * latter ({@link #all()} emits {@link ElementEvent} which tells element type, others emit just bare elements.
 * <p>
 * For more details and code examples, see <a href='https://github.com/new-mikha/rxhelpers'>README.MD</a>
 * <p>
 *
 * @param <T> the element type
 */
public class ReplayRemoveSubject<T> extends Subject<T> {

    private final Map<Object, ElementEvent<T>> elements = new LinkedHashMap<>();
    private long elementsRevision; // increments with every elements.put() or .remove()
    private final Function<T, Object> keySelector;
    private final Subject<ElementEvent<T>> eventsSubject = PublishSubject.create();
    private volatile boolean isDone;
    private final AtomicInteger subscribersCount = new AtomicInteger();


    @SuppressWarnings("WeakerAccess")
    public ReplayRemoveSubject() {
        this(t -> t);
    }


    @SuppressWarnings("WeakerAccess")
    public ReplayRemoveSubject(Function<T, Object> keySelector) {
        this.keySelector = keySelector;
    }


    public static <T> ReplayRemoveSubject<T> create() {
        return new ReplayRemoveSubject<>();
    }


    @SuppressWarnings("unused")
    public static <T> ReplayRemoveSubject<T> create(Function<T, Object> keySelector) {
        return new ReplayRemoveSubject<>(keySelector);
    }

    @SuppressWarnings("unused")
    public Observable<T> snapshot() {

        return Observable.fromIterable(
            getSnapshot(null, null)
        ).map(ElementEvent::getElement);
    }


    @SuppressWarnings("unused")
    public Observable<T> adds() {

        return getObservable(event ->
            event.getEventType() == ElementEventType.Add
                ? event.getElement()
                : null);
    }


    @SuppressWarnings("unused")
    public Observable<T> updates() {

        return getObservable(event ->
            event.getEventType() == ElementEventType.Update
                ? event.getElement()
                : null);
    }


    @SuppressWarnings("unused")
    public Observable<T> removes() {

        return getObservable(event ->
            event.getEventType() == ElementEventType.Remove
                ? event.getElement()
                : null);
    }


    @SuppressWarnings({"WeakerAccess", "unused"})
    public Observable<ElementEvent<T>> all() {

        return getObservable(event -> event);
    }


    @Override
    protected void subscribeActual(Observer<? super T> observer) {

        getObservable(event ->
            event.getEventType() == ElementEventType.Remove
                ? null
                : event.getElement()
        )
            .subscribe(observer);
    }


    private <K> Observable<K> getObservable(Function<ElementEvent<T>, K> filterPredicate) {

        return Observable.<K>create(source ->
            subscribeToAll(new Observer<ElementEvent<T>>() {
                @Override
                public void onSubscribe(Disposable d) {
                    source.setDisposable(d);
                }

                @Override
                public void onNext(ElementEvent<T> event) {
                    final K result = filterPredicate.apply(event);
                    if (result != null)
                        source.onNext(result);
                }

                @Override
                public void onError(Throwable e) {
                    source.onError(e);
                }

                @Override
                public void onComplete() {
                    source.onComplete();
                }
            })
        )
            .doOnSubscribe(unused -> subscribersCount.getAndIncrement())
            .doOnDispose(subscribersCount::getAndDecrement);
    }


    private void subscribeToAll(Observer<? super ElementEvent<T>> observer) {

        final AtomicLong snapshotRevision =
            new AtomicLong();

        final UnicastSubject<ElementEvent<T>> unicast =
            UnicastSubject.create();

        final List<ElementEvent<T>> elementsSnapshot =
            getSnapshot(snapshotRevision, unicast);

        // Observable.fromIterable works noticeably longer than this, also it allows to spit the snapshot out ASAP,
        // without waiting for all the machinery for the subsequent events:
        for (ElementEvent<T> elementEvent : elementsSnapshot) {
            // in the snapshot, eventType is always current
            observer.onNext(elementEvent);
        }

        final long snapshotRevisionValue =
            snapshotRevision.get();

        final Observable<ElementEvent<T>> subsequentEvents = unicast
            .filter(event -> event.getRevision() > snapshotRevisionValue);

        // Unicast queues up events until subscribed, then all new ones are just relayed. This way it's ensured there
        // are no events lost, and at the same time there is no infinite in-mem collection in the unicast. But some
        // events might be double-dipped - to prevent that, the event revision compared with one of the snapshot:
        subsequentEvents
            .subscribe(observer);
    }


    private List<ElementEvent<T>> getSnapshot(AtomicLong snapshotRevision, Observer<ElementEvent<T>> observer) {

        synchronized (elements) {

            if (snapshotRevision != null)
                snapshotRevision.set(elementsRevision);

            // Inside the synchronized block for a bit of optimisation. It can't be subscribed after the snapshot
            // created. If it's subscribed before there is a bigger chance there will be redundant values (filtered out
            // later by revision anyway, but no need to spend time & memory adding them). So better subscribe it here:
            if (observer != null)
                eventsSubject.subscribe(observer);

            return new ArrayList<>(elements.values());
        }
    }


    @Override
    public void onSubscribe(Disposable d) {

        if (isDone)
            d.dispose();
    }


    /**
     * A thread-safety note - this method and {@link ##onRemove} can be called simultaneously from different threads.
     */
    @Override
    public void onNext(T t) {

        // ElementEventType might change later if it's actually an update:
        onNext(t, ElementEventType.Add);
    }


    /**
     * A thread-safety note - this method and {@link ##onNext(T)} can be called simultaneously from different threads.
     */
    @SuppressWarnings("unused")
    public void onRemove(T t) {

        onNext(t, ElementEventType.Remove);
    }


    private void onNext(T t, ElementEventType eventType) {

        ObjectHelper.requireNonNull(t, "onNext called with null. Null values are generally not allowed in 2.x operators and sources.");
        if (isDone) {
            return;
        }

        // An alternative to 'synchronized' would be using readonly collections, i.e. assign a new one to the volatile
        // field 'elements' each time (accompanied by a revision number, e.g. as a Pair). So every read automatically
        // gets a snapshot. However the overall performance gain would most probably be negative because every onNext()
        // would create much more objects than now, which will affect GC. So just synchronized(), no showing off here:
        synchronized (elements) {
            final Object key =
                keySelector.apply(t);

            final long revision =
                ++elementsRevision;

            final ElementEventType effectiveEventType;
            if (eventType == ElementEventType.Remove) {
                effectiveEventType = eventType;
                elements.remove(key);
            } else {
                final boolean isNew =
                    null == elements.put(key, new ElementEvent<>(key, t, ElementEventType.Current, revision));

                effectiveEventType =
                    isNew ? ElementEventType.Add : ElementEventType.Update;
            }

            final ElementEvent<T> elementEvent =
                new ElementEvent<>(
                    key,
                    t,
                    effectiveEventType,
                    revision);

            // synchronized() above is mainly to make getSnapshot() & revision consistent and avoid reordering issues when
            // writing to a non-concurrent map, even though it can be requested in the onNext contract to do it only from
            // non-overlapping threads. But it's in use anyway, so add eventsSubject inside too, making the whole
            // method thread-safe:
            eventsSubject.onNext(elementEvent);
        }
    }


    @Override
    public void onError(Throwable e) {

        ObjectHelper.requireNonNull(e, "onError called with null. Null values are generally not allowed in 2.x operators and sources.");

        if (isDone) {
            RxJavaPlugins.onError(e);
            return;
        }
        isDone = true;

        eventsSubject.onError(e);
    }


    @Override
    public void onComplete() {

        if (isDone) {
            return;
        }
        isDone = true;

        eventsSubject.onComplete();
    }


    @Override
    public boolean hasObservers() {
        return subscribersCount.get() > 0;
    }


    @Override
    public boolean hasThrowable() {
        return eventsSubject.hasThrowable();
    }


    @Override
    public boolean hasComplete() {
        return eventsSubject.hasComplete();
    }


    @Override
    public Throwable getThrowable() {
        return eventsSubject.getThrowable();
    }


    /* test */ int size() {
        return elements.size();
    }


    /* test */ boolean hasValue() {
        return elements.size() != 0;
    }


    /* test */ Object[] getValues() {
        AtomicLong unused = new AtomicLong();
        return Iterables.toArray(
            getSnapshot(unused, null)
                .stream()
                .map(ElementEvent::getElement)
                .collect(Collectors.toList()),
            Object.class);
    }

    /* test */
    @SuppressWarnings("unchecked")
    T getValue() {
        final Object[] values = getValues();
        if (values.length == 0)
            return null;
        return (T)values[values.length - 1];
    }


    /* test */ int observerCount() {
        return subscribersCount.get();
    }


}
