package main.java.com.fabflix;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FabflixXMLParser {
    private static final int BATCH_SIZE = 1000;
    private final Connection conn;
    private final Map<String, String> movieCache = new ConcurrentHashMap<>();
    private final Map<String, String> starCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> genreCache = new ConcurrentHashMap<>();

    public FabflixXMLParser(Connection conn) throws SQLException {
        this.conn = conn;
        conn.setAutoCommit(false);
        loadExistingData();
    }

    private void loadExistingData() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, title, year FROM movies");
            while (rs.next()) {
                String key = rs.getString("title").toLowerCase() + "|" + rs.getInt("year");
                movieCache.put(key, rs.getString("id"));
            }

            rs = stmt.executeQuery("SELECT id, name FROM stars");
            while (rs.next()) {
                starCache.put(rs.getString("name").toLowerCase(), rs.getString("id"));
            }

            rs = stmt.executeQuery("SELECT id, name FROM genres");
            while (rs.next()) {
                genreCache.put(rs.getString("name").toLowerCase(), rs.getInt("id"));
            }
        }
    }

    public void parseMovies(String filePath) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        MovieHandler handler = new MovieHandler();
        saxParser.parse(filePath, handler);
        handler.finalizeBatch();
        conn.commit();
    }

    public void parseCasts(String filePath) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        CastHandler handler = new CastHandler();
        saxParser.parse(filePath, handler);
        handler.finalizeBatch();
        conn.commit();
    }

    private class MovieHandler extends DefaultHandler {
        private final List<Movie> movieBatch = new ArrayList<>(BATCH_SIZE);
        private final List<GenreRelation> genreBatch = new ArrayList<>(BATCH_SIZE*3);
        private Movie currentMovie;
        private StringBuilder currentValue;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("film".equalsIgnoreCase(qName)) {
                currentMovie = new Movie();
            }
            currentValue = new StringBuilder();
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            currentValue.append(new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            try {
                String value = currentValue.toString().trim();
                if (currentMovie != null) {
                    switch (qName.toLowerCase()) {
                        case "fid":
                            currentMovie.fid = value;
                            break;
                        case "t":
                            currentMovie.title = value;
                            break;
                        case "year":
                            currentMovie.year = parseYear(value);
                            break;
                        case "dirn":
                            currentMovie.director = value;
                            break;
                        case "cat":
                            if (!value.isEmpty()) currentMovie.genres.add(value.toLowerCase());
                            break;
                        case "film":
                            processMovie();
                            break;
                    }
                }
            } catch (Exception e) {
                handleError(qName, currentValue.toString(), e);
            }
        }

        private Integer parseYear(String value) {
            try {
                return Integer.parseInt(value.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private void processMovie() throws SQLException {
            if (isValidMovie(currentMovie)) {
                String movieKey = currentMovie.title.toLowerCase() + "|" + currentMovie.year;
                if (!movieCache.containsKey(movieKey)) {
                    movieBatch.add(currentMovie);
                    movieCache.put(movieKey, generateMovieId(currentMovie));
                }
                currentMovie.id = movieCache.get(movieKey);
                processGenres(currentMovie);
                if (movieBatch.size() >= BATCH_SIZE) insertBatch();
            }
            currentMovie = null;
        }

        private void processGenres(Movie movie) {
            for (String genre : movie.genres) {
                Integer genreId = genreCache.get(genre);
                if (genreId != null) {
                    genreBatch.add(new GenreRelation(genreId, movie.id));
                } else {
                    System.err.println("Genre not found: " + genre);
                }
            }
        }

        // 修改后的genre插入逻辑
        private void insertGenre(String genre) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO genres (name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, genre);
                int affectedRows = pstmt.executeUpdate();

                if (affectedRows > 0) {
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int newId = rs.getInt(1);
                            genreCache.put(genre, newId);
                        }
                    }
                } else {
                    // 处理重复插入的情况
                    try (PreparedStatement selectStmt = conn.prepareStatement(
                            "SELECT id FROM genres WHERE name = ?")) {
                        selectStmt.setString(1, genre);
                        try (ResultSet rs = selectStmt.executeQuery()) {
                            if (rs.next()) {
                                genreCache.put(genre, rs.getInt(1));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                handleError("genre_insert", genre, e);
            }
        }

        void finalizeBatch() throws SQLException {
            if (!movieBatch.isEmpty()) insertBatch();
            insertGenreRelations();
        }

        private void insertBatch() throws SQLException {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO movies (id, title, year, director) VALUES (?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE title=title")) {
                for (Movie m : movieBatch) {
                    pstmt.setString(1, m.id);
                    pstmt.setString(2, m.title);
                    pstmt.setObject(3, m.year, Types.INTEGER);
                    pstmt.setString(4, m.director);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                movieBatch.clear();
            }
        }

        private void insertGenreRelations() throws SQLException {
            Map<String, List<Integer>> grouped = new HashMap<>();
            genreBatch.forEach(gr -> grouped
                    .computeIfAbsent(gr.movieId, k -> new ArrayList<>())
                    .add(gr.genreId));

            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT IGNORE INTO genres_in_movies (genreId, movieId) VALUES (?,?)")) {
                grouped.forEach((movieId, genreIds) -> {
                    genreIds.forEach(genreId -> {
                        try {
                            pstmt.setInt(1, genreId);
                            pstmt.setString(2, movieId);
                            pstmt.addBatch();
                        } catch (SQLException e) {
                            handleError("genre_relation", movieId, e);
                        }
                    });
                });
                pstmt.executeBatch();
                genreBatch.clear();
            }
        }

        private boolean isValidMovie(Movie m) {
            return m.title != null && !m.title.isEmpty() && m.year != null;
        }

        private String generateMovieId(Movie m) {
            return "tt" + Math.abs((m.title + m.year).hashCode());
        }
    }

    private class CastHandler extends DefaultHandler {
        private final List<Star> starBatch = new ArrayList<>(BATCH_SIZE);
        private final List<StarRelation> relationBatch = new ArrayList<>(BATCH_SIZE*3);
        private String currentMovieId;
        private Star currentStar;
        private StringBuilder currentValue;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if ("m".equalsIgnoreCase(qName)) {
                currentMovieId = null;
            } else if ("a".equalsIgnoreCase(qName)) {
                currentStar = new Star();
            }
            currentValue = new StringBuilder();
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            currentValue.append(new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            try {
                String value = currentValue.toString().trim();
                if ("fid".equalsIgnoreCase(qName)) {
                    currentMovieId = movieCache.get(value.toLowerCase());
                } else if (currentStar != null) {
                    switch (qName.toLowerCase()) {
                        case "stagename":
                            currentStar.name = value;
                            break;
                        case "a":
                            processStar();
                            break;
                    }
                }
            } catch (Exception e) {
                handleError(qName, currentValue.toString(), e);
            }
        }

        private void processStar() throws SQLException {
            if (currentStar.name != null && !currentStar.name.isEmpty() && currentMovieId != null) {
                String starKey = currentStar.name.toLowerCase();
                if (!starCache.containsKey(starKey)) {
                    starBatch.add(currentStar);
                    starCache.put(starKey, generateStarId(currentStar));
                }
                relationBatch.add(new StarRelation(starCache.get(starKey), currentMovieId));
                if (starBatch.size() >= BATCH_SIZE) insertBatch();
            }
            currentStar = null;
        }

        void finalizeBatch() throws SQLException {
            if (!starBatch.isEmpty()) insertBatch();
            insertRelations();
        }

        private void insertBatch() throws SQLException {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO stars (id, name) VALUES (?,?) ON DUPLICATE KEY UPDATE name=name")) {
                for (Star s : starBatch) {
                    pstmt.setString(1, s.id);
                    pstmt.setString(2, s.name);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                starBatch.clear();
            }
        }

        private void insertRelations() throws SQLException {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT IGNORE INTO stars_in_movies (starId, movieId) VALUES (?,?)")) {
                relationBatch.forEach(sr -> {
                    try {
                        pstmt.setString(1, sr.starId);
                        pstmt.setString(2, sr.movieId);
                        pstmt.addBatch();
                    } catch (SQLException e) {
                        handleError("star_relation", sr.starId, e);
                    }
                });
                pstmt.executeBatch();
                relationBatch.clear();
            }
        }

        private String generateStarId(Star s) {
            return "nm" + Math.abs(s.name.hashCode());
        }
    }

    private static class Movie {
        String id;
        String fid;
        String title;
        Integer year;
        String director;
        List<String> genres = new ArrayList<>();
    }

    private static class Star {
        String id;
        String name;
    }

    private static class GenreRelation {
        final int genreId;
        final String movieId;

        public GenreRelation(int genreId, String movieId) {
            this.genreId = genreId;
            this.movieId = movieId;
        }
    }

    private static class StarRelation {
        final String starId;
        final String movieId;

        public StarRelation(String starId, String movieId) {
            this.starId = starId;
            this.movieId = movieId;
        }
    }

    private void handleError(String element, String value, Exception e) {
        System.err.printf("Error processing element [%s] with value [%s]: %s%n",
                element, value, e.getMessage());
    }

    public static void main(String[] args) throws Exception {
//        Properties props = new Properties();
//        props.load(FabflixXMLParser.class.getResourceAsStream("/db.properties"));
//
//        try (Connection conn = DriverManager.getConnection(
//                props.getProperty("db.url"),
//                props.getProperty("db.user"),
//                props.getProperty("db.password"))) {
//
//            FabflixXMLParser parser = new FabflixXMLParser(conn);
//            parser.parseMovies("mains243.xml");
//            parser.parseCasts("casts124.xml");
//        }
    }
}
