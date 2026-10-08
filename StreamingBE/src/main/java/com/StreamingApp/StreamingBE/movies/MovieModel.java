package com.StreamingApp.StreamingBE.movies;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class MovieModel {
    @Id
    private int movieId;
    private String movie_name;
    private String movie_description;
    private String movie_release_date;
    private String movie_genre;
    private String duration;
    private String movie_image;
    private String Hls_reference;

}
