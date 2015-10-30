RxJava 2.0 Backport
==============

This is a backport of RxJava 2.0 to Java 6 API level.

Features
--------

  - The backpressure- and Reactive-Streams supporting `Observable` class and related `Subject`s. Note that this may change to `Flowable`
  - The non-backpressured `NbpObservable` class and `NbpSubject`s. Note that this may become `Observable`
  - The `Single` class for single-valued deferred computations.
  - The `Completable` class for valueless event composition (i.e., `onError` and `onComplete` only).
  - Includes all (unmerged) bugfixes from RxJava 2.0 (Java 8) and a few recent feature requests.
  - +3000 unit tests (although ~100 is skipped because of semantic differences with 1.x, these may get resolved once)
  - Includes a partial backport of `Objects` and `Optional`.



Package naming
--------------

The version 2.0.0-RC2 uses the package naming `hu.akarnokd.rxjava2` because it is unclear what
the final package naming of RxJava 2.0 will be. In addition, this package naming
allows inclusion of both RxJava 2.0 and this backport without any package conflicts although
with redundant naming.

This may, however, change based on feedbacks before 2.0.0 final.


Import
------

Gradle:

```
compile 'com.github.akarnokd:rxjava2-backport:2.0.0-RC2'
```


Ivy:

```xml
<dependency org="com.github.akarnokd" name="rxjava2-backport" rev='2.0.0-RC2'/>
```

Releases
--------

<a href='https://travis-ci.org/akarnokd/rxjava2-backport/builds'><img src='https://travis-ci.org/akarnokd/rxjava2-backport.svg?branch=master'></a>