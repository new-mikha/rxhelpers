## The library contains ReplayRemoveSubject - an addition to RxJava 2 ##

The subject works like a `ReplaySubject`, but it also allows to remove elements from the buffer so late observers do not see removed ones, and the buffer size can be limited manually.

It can be useful in scenarios where e.g. a cold observable of an updatable database is required. Notice that the buffer is distinct (see details below)


### Installation ###
Can be built by running `./gradlew` (`gradlew` on Windows) from the working directory. Result is a JAR file in build\libs directory, with POM and sources generated too, so it's easy to either use JAR directly or upload JAR's and POM to an organisation-specific repository first. 

Or simply take the whole src/main/java/rxhelpers folder into your project - just don't forget to add a reference to your NOTICES file and keep the original license headers, in according to the Apache License used here.

### Usage ###
The subject has a variety of methods to get combinations of snapshot, inserts, updates, and removal events, but in a simplest way it looks like this:

    ReplayRemoveSubject<Integer> subject = ReplayRemoveSubject.create();

    subject.onNext(1);
    subject.onNext(2);
    subject.onNext(3);
    subject.subscribe(x -> System.out.println("SubscrA: " + x));
    // So far it's the same as ReplaySubject: output above is 1, 2, 3
    System.out.println("(I) ----"); 

    subject.onRemove(2); // onRemove() is a new method
    subject.subscribe(x -> System.out.println("SubscrB: " + x)); 
    System.out.println("(II) ----");
    // output between (I) and (II):   1, 3   (all from SubscrB)

    subject.onNext(4);
    // output after (II):   4, 4   (one from SubscrA, other from SubscrB)



The buffer is distinct. So even though updating an element is relayed, a late observer will see an element updated earlier only once:

    ReplayRemoveSubject<Integer> subject = ReplayRemoveSubject.create();

    subject.subscribe(x -> System.out.println("SubscrA: " + x));
    subject.onNext(1);
    subject.onNext(1); // update
    subject.onNext(2);
    // output: 1, 1, 2

    subject.subscribe(x -> System.out.println("SubscrB: " + x)); // output: 1, 2
	

All public methods are thread safe including onNext and onRemove (i.e. both can be called from multiple threads), and the subject supposedly guarantees (unless there is a bug) that in any single subscription there will be no missed emissions, and an element will not be emitted more than it's been onNext'ed. 


By default, an equality is checked by the element's equal() and hashCode() methods. If it's not enough, there is a constructor that accepts a key selector:


    interface Order {
        int getId();
    }
    
    ReplayRemoveSubject<Order> subject = ReplayRemoveSubject.create(Order::getId);


There are separate methods to get observables of additions, updates, removals, snapshot, or all those events types combined:

    Observable<T> adds();
    Observable<T> updates();
    Observable<T> removes();
    Observable<T> snapshot(); 
    Observable<ElementEvent<T>> all();

Notice that the latter emits instances of ElementEvent, which wraps T and also can tell which kind of emission is it: Current (or snapshot, i.e. an element was in the buffer at the moment of subscription), Add, Update, or Remove.

Notice also that `snapshot()` is an observable just for consistency with the other methods, internally it's Observable.fromIterable() of a fully synchronous collection. 

If the subject is subscribed as-is (rather than via one of the specific methods above), it emits combination of Current, Add and Update events (i.e. WITHOUT Remove), with Current coming out first - even though it doesn't provide an event type. If event type is required, use the `all()` observable.

As a draw-back for adding the above functionality, the subject is slower than the original ReplaySubject. Of course, it can be noticed only on high volumes & frequencies, but it's something to keep in mind.
