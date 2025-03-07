package com.example.androidcicd.movie;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.AggregateField;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class MovieProvider {
    private static MovieProvider movieProvider;
    private final ArrayList<Movie> movies;
    private final CollectionReference movieCollection;

    public static boolean IS_TEST = false;
    public static long TEST_GET_MOVIE_COUNT = 0;

    private MovieProvider(FirebaseFirestore firestore) {
        movies = new ArrayList<>();
        movieCollection = firestore.collection("movies");
    }

    public static void setInstanceForTesting(FirebaseFirestore firestore) {
        movieProvider = new MovieProvider(firestore);
    }

    public interface DataStatus {
        void onDataUpdated();
        void onError(String error);
    }

    public void listenForUpdates(final DataStatus dataStatus) {
        movieCollection.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                dataStatus.onError(error.getMessage());
                return;
            }
            movies.clear();
            if (snapshot != null) {
                for (QueryDocumentSnapshot item : snapshot) {
                    movies.add(item.toObject(Movie.class));
                }
                dataStatus.onDataUpdated();
            }
        });
    }

    public static MovieProvider getInstance(FirebaseFirestore firestore) {
        if (movieProvider == null)
            movieProvider = new MovieProvider(firestore);
        return movieProvider;
    }

    public ArrayList<Movie> getMovies() {
        return movies;
    }

    public Task<Long> getMovieCountWithTitle(String title) {
        return movieCollection.whereEqualTo("title", title)
                .count()
                .get(AggregateSource.SERVER)
                .onSuccessTask(aggregateQuerySnapshot ->
                    Tasks.forResult(aggregateQuerySnapshot.get(AggregateField.count())));
    }

    public Task<DocumentReference> updateMovie(Movie movie, String title, String genre, int year) {
        if (IS_TEST) {
            if (TEST_GET_MOVIE_COUNT > 0) {
                throw new IllegalArgumentException("Movie has a duplicate title!");
            }

            movie.setTitle(title);
            movie.setGenre(genre);
            movie.setYear(year);
            DocumentReference docRef = movieCollection.document(movie.getId());
            if (validMovie(movie, docRef)) {
                docRef.set(movie);
            }

            return null;
        }

        return getMovieCountWithTitle(title)
            .onSuccessTask(count -> {
                if (count != 0) {
                    return Tasks.forException(
                            new IllegalArgumentException("Movie has a duplicate title!"));
                }

                movie.setTitle(title);
                movie.setGenre(genre);
                movie.setYear(year);
                DocumentReference docRef = movieCollection.document(movie.getId());
                if (validMovie(movie, docRef)) {
                    return docRef.set(movie).onSuccessTask(_void -> Tasks.forResult(docRef));
                } else {
                    return Tasks.forException(new IllegalArgumentException("Invalid Movie!"));
                }
            });
    }

    public Task<DocumentReference> addMovie(Movie movie) {
        if (IS_TEST) {
            if (TEST_GET_MOVIE_COUNT > 0) {
                throw new IllegalArgumentException("Movie has a duplicate title!");
            }

            DocumentReference documentReference = movieCollection.document();
            movie.setId(documentReference.getId());
            if (validMovie(movie, documentReference)) {
                documentReference.set(movie);
            } else {
                throw new IllegalArgumentException("Invalid Movie!");
            }

            return null;
        }

        return getMovieCountWithTitle(movie.getTitle())
            .onSuccessTask(count -> {
                if (count != 0) {
                    return Tasks.forException(
                            new IllegalArgumentException("Movie has a duplicate title!"));
                }

                DocumentReference documentReference = movieCollection.document();
                movie.setId(documentReference.getId());
                if (validMovie(movie, documentReference)) {
                    documentReference.set(movie);
                } else {
                    return Tasks.forException(new IllegalArgumentException("Invalid Movie!"));
                }

                return Tasks.forResult(documentReference);
            });
    }

    public void deleteMovie(Movie movie) {
        DocumentReference docRef = movieCollection.document(movie.getId());
        docRef.delete();
    }

    public boolean validMovie(Movie movie, DocumentReference docRef) {
        return movie.getId().equals(docRef.getId()) && !movie.getTitle().isEmpty() && !movie.getGenre().isEmpty() && movie.getYear() > 0;
    }
}
