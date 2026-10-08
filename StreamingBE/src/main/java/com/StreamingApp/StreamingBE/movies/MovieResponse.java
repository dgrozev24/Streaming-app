package com.StreamingApp.StreamingBE.movies;

public record MovieResponse(int id, String title, String description, String releaseDate,
                            String genre, String duration, String imageUrl, String hlsReference) {
    public static MovieResponse from(MovieModel movie) {
        return new MovieResponse(movie.getMovieId(), movie.getMovie_name(),
                movie.getMovie_description(), movie.getMovie_release_date(),
                movie.getMovie_genre(), movie.getDuration(), movie.getMovie_image(),
                movie.getHls_reference());
    }
}

