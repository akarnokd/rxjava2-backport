/**
 * Copyright 2015 David Karnok and Netflix, Inc.
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

package hu.akarnokd.rxjava2.nbp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import hu.akarnokd.rxjava2.NbpObservable;
import hu.akarnokd.rxjava2.functions.BiFunction;
import hu.akarnokd.rxjava2.nbp.NbpCovarianceTest.*;

public class NbpReduceTests {

    @Test
    public void reduceInts() {
        NbpObservable<Integer> o = NbpObservable.just(1, 2, 3);
        int value = o.reduce(new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer t1, Integer t2) {
                return t1 + t2;
            }
        }).toBlocking().single();

        assertEquals(6, value);
    }

    @SuppressWarnings("unused")
    @Test
    public void reduceWithObjects() {
        NbpObservable<Movie> horrorMovies = NbpObservable.<Movie> just(new HorrorMovie());

        NbpObservable<Movie> reduceResult = horrorMovies.scan(new BiFunction<Movie, Movie, Movie>() {
            @Override
            public Movie apply(Movie t1, Movie t2) {
                return t2;
            }
        }).takeLast(1);

        NbpObservable<Movie> reduceResult2 = horrorMovies.reduce(new BiFunction<Movie, Movie, Movie>() {
            @Override
            public Movie apply(Movie t1, Movie t2) {
                return t2;
            }
        });
    }

    /**
     * Reduce consumes and produces T so can't do covariance.
     * 
     * https://github.com/ReactiveX/RxJava/issues/360#issuecomment-24203016
     */
    @SuppressWarnings("unused")
    @Test
    public void reduceWithCovariantObjects() {
        NbpObservable<Movie> horrorMovies = NbpObservable.<Movie> just(new HorrorMovie());

        NbpObservable<Movie> reduceResult2 = horrorMovies.reduce(new BiFunction<Movie, Movie, Movie>() {
            @Override
            public Movie apply(Movie t1, Movie t2) {
                return t2;
            }
        });
    }

    /**
     * Reduce consumes and produces T so can't do covariance.
     * 
     * https://github.com/ReactiveX/RxJava/issues/360#issuecomment-24203016
     */
    @Test
    public void reduceCovariance() {
        // must type it to <Movie>
        NbpObservable<Movie> horrorMovies = NbpObservable.<Movie> just(new HorrorMovie());
        libraryFunctionActingOnMovieNbpObservables(horrorMovies);
    }

    /*
     * This accepts <Movie> instead of <? super Movie> since `reduce` can't handle covariants
     */
    public void libraryFunctionActingOnMovieNbpObservables(NbpObservable<Movie> obs) {

        obs.reduce(new BiFunction<Movie, Movie, Movie>() {
            @Override
            public Movie apply(Movie t1, Movie t2) {
                return t2;
            }
        });
    }

}