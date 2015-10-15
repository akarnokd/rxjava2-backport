RxJava 2.0 Backport
==============

This is a backport of RxJava 2.0 to Java 6 API level.

Package naming
--------------

The version 2.0.0-RC1 uses the package naming `hu.akarnokd.rxjava2` because it is unclear what
the final package naming of RxJava 2.0 will be. In addition, this package naming
allows inclusion of both RxJava 2.0 and this backport without any package conflicts although
with redundant naming.

This may, however, change based on feedbacks before 2.0.0 final.


Import
------

Gradle:

```
compile 'com.github.akarnokd:rxjava2-backport:2.0.0'
```


Ivy:

```xml
<dependency org="com.github.akarnokd" name="rxjava2-backport" rev='2.0.0'/>
```

Releases
--------

<a href='https://travis-ci.org/akarnokd/rxjava2-backport/builds'><img src='https://travis-ci.org/akarnokd/rxjava2-backport.svg?branch=master'></a>