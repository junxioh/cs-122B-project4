package main.java.com.fabflix.util;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Level;

public class MovieXMLParser extends DefaultHandler {
    private static final Logger LOGGER = Logger.getLogger(MovieXMLParser.class.getName());
    private static final int BATCH_SIZE = 1000;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    
    // 缓存
    private final Map<String, String> movieIdCache = new ConcurrentHashMap<>();
    private final Map<String, String> starIdCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> genreIdCache = new ConcurrentHashMap<>();
    private final Set<String> processedMovies = ConcurrentHashMap.newKeySet();
    private final Set<String> processedStars = ConcurrentHashMap.newKeySet();
    private final Set<String> processedGenres = ConcurrentHashMap.newKeySet();
    
    // 批处理队列
    private final BlockingQueue<MovieData> movieQueue = new LinkedBlockingQueue<>(BATCH_SIZE);
    private final BlockingQueue<StarData> starQueue = new LinkedBlockingQueue<>(BATCH_SIZE);
    private final BlockingQueue<GenreData> genreQueue = new LinkedBlockingQueue<>(BATCH_SIZE);
    
    // 统计信息
    private final AtomicInteger moviesProcessed = new AtomicInteger(0);
    private final AtomicInteger starsProcessed = new AtomicInteger(0);
    private final AtomicInteger genresProcessed = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);
    private final AtomicInteger skipped = new AtomicInteger(0);
    
    // 当前解析状态
    private StringBuilder currentValue;
    private MovieData currentMovie;
    private StarData currentStar;
    private String currentGenre;
    private boolean inFilm = false;
    private boolean inStar = false;
    private boolean inCast = false;
    private String currentMovieId;
    
    // 线程池和消费者
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private final List<Future<?>> consumerFutures = new ArrayList<>();
    
    private volatile boolean isParsingComplete = false;
    
    // 新增
    private final List<Pair<String, String>> starMovieRelations = new ArrayList<>();
    
    public MovieXMLParser() {
        // 启动消费者线程
        startConsumers();
    }
    
    private void startConsumers() {
        // 电影消费者
        consumerFutures.add(executorService.submit(() -> {
            int emptyCount = 0;
            while (!Thread.currentThread().isInterrupted() && (!isParsingComplete || !movieQueue.isEmpty())) {
                try {
                    List<MovieData> batch = new ArrayList<>();
                    int drained = movieQueue.drainTo(batch, BATCH_SIZE);
                    
                    if (drained > 0) {
                        emptyCount = 0;
                        processMovieBatch(batch);
                        LOGGER.info("Processed movie batch of size: " + batch.size());
                    } else if (isParsingComplete) {
                        emptyCount++;
                        if (emptyCount > 10) { // 连续10次空队列，认为处理完成
                            LOGGER.info("Movie queue empty for too long, consumer exiting");
                            break;
                        }
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            LOGGER.info("Movie consumer thread finished");
        }));
        
        // 明星消费者
        consumerFutures.add(executorService.submit(() -> {
            int emptyCount = 0;
            while (!Thread.currentThread().isInterrupted() && (!isParsingComplete || !starQueue.isEmpty())) {
                try {
                    List<StarData> batch = new ArrayList<>();
                    int drained = starQueue.drainTo(batch, BATCH_SIZE);
                    
                    if (drained > 0) {
                        emptyCount = 0;
                        processStarBatch(batch);
                        LOGGER.info("Processed star batch of size: " + batch.size());
                    } else if (isParsingComplete) {
                        emptyCount++;
                        if (emptyCount > 10) { // 连续10次空队列，认为处理完成
                            LOGGER.info("Star queue empty for too long, consumer exiting");
                            break;
                        }
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            LOGGER.info("Star consumer thread finished");
        }));
        
        // 类型消费者
        consumerFutures.add(executorService.submit(() -> {
            int emptyCount = 0;
            while (!Thread.currentThread().isInterrupted() && (!isParsingComplete || !genreQueue.isEmpty())) {
                try {
                    List<GenreData> batch = new ArrayList<>();
                    int drained = genreQueue.drainTo(batch, BATCH_SIZE);
                    
                    if (drained > 0) {
                        emptyCount = 0;
                        processGenreBatch(batch);
                        LOGGER.info("Processed genre batch of size: " + batch.size());
                    } else if (isParsingComplete) {
                        emptyCount++;
                        if (emptyCount > 10) { // 连续10次空队列，认为处理完成
                            LOGGER.info("Genre queue empty for too long, consumer exiting");
                            break;
                        }
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            LOGGER.info("Genre consumer thread finished");
        }));
    }
    
    public void parseMovies(String xmlFile) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            
            // 使用ISO-8859-1编码
            try (FileInputStream fis = new FileInputStream(new File(xmlFile))) {
                org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
                is.setEncoding("ISO-8859-1");
                
                // 重置状态
                isParsingComplete = false;
                
                // 开始解析
                parser.parse(is, this);
                
                // 标记解析完成
                isParsingComplete = true;
                
                LOGGER.info("XML parsing completed, waiting for consumers to finish...");
                
                // 等待所有消费者完成
                for (Future<?> future : consumerFutures) {
                    try {
                        future.get(30, TimeUnit.SECONDS); // 设置超时时间
                    } catch (TimeoutException e) {
                        LOGGER.warning("Consumer thread timeout, forcing shutdown");
                        future.cancel(true);
                    }
                }
                
                // 关闭线程池
                executorService.shutdown();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.warning("Forcing executor service shutdown");
                    executorService.shutdownNow();
                }
                
                // 打印统计信息
                LOGGER.info("Import completed:");
                LOGGER.info("Movies processed: " + moviesProcessed.get());
                LOGGER.info("Stars processed: " + starsProcessed.get());
                LOGGER.info("Genres processed: " + genresProcessed.get());
                LOGGER.info("Errors encountered: " + errors.get());
                LOGGER.info("Skipped entries: " + skipped.get());

                // 新增：统一插入明星-电影关系
                insertAllStarMovieRelations();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error parsing XML: " + e.getMessage(), e);
        } finally {
            // 确保线程池被关闭
            if (!executorService.isShutdown()) {
                executorService.shutdownNow();
            }
        }
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        currentValue = new StringBuilder();
        
        switch (qName.toLowerCase()) {
            case "film":
                inFilm = true;
                currentMovie = new MovieData();
                break;
            case "m":
                inCast = true;
                currentStar = new StarData();
                break;
            case "a":
                inStar = true;
                break;
            case "cat":
                currentGenre = null;
                break;
        }
    }
    
    @Override
    public void characters(char[] ch, int start, int length) {
        if (currentValue != null) {
            currentValue.append(new String(ch, start, length).trim());
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) {
        String value = currentValue.toString().trim();
        
        try {
            if (inFilm) {
                processFilmElement(qName, value);
            } else if (inCast) {
                processCastElement(qName, value);
            } else if (inStar) {
                processStarElement(qName, value);
            } else if (qName.equalsIgnoreCase("cat")) {
                processGenreElement(value);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing element " + qName + " with value: " + value, e);
            errors.incrementAndGet();
        }
    }
    
    private void processFilmElement(String qName, String value) {
        switch (qName.toLowerCase()) {
            case "fid":
                if (value != null && !value.trim().isEmpty()) {
                    currentMovie.id = value.trim();
                    currentMovieId = value.trim();
                } else {
                    LOGGER.warning("Invalid or empty fid value encountered");
                    currentMovie.id = null;
                    currentMovieId = null;
                }
                break;
            case "t":
                currentMovie.title = value;
                break;
            case "year":
                try {
                    if (value != null && !value.trim().isEmpty() && !value.equals("19yy")) {
                        currentMovie.year = Integer.parseInt(value.trim());
                    } else {
                        LOGGER.warning("Invalid year value: " + value);
                        currentMovie.year = null;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid year value: " + value);
                    currentMovie.year = null;
                }
                break;
            case "dir":
                currentMovie.director = value;
                break;
            case "film":
                inFilm = false;
                if (isValidMovie(currentMovie)) {
                    if (currentMovie.id != null && !processedMovies.contains(currentMovie.id)) {
                        try {
                            movieQueue.put(currentMovie);
                            processedMovies.add(currentMovie.id);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else if (currentMovie.id == null) {
                        LOGGER.warning("Skipping movie with null id: " + currentMovie.title);
                        skipped.incrementAndGet();
                    } else {
                        LOGGER.info("Skipping duplicate movie: " + currentMovie.title);
                        skipped.incrementAndGet();
                    }
                } else {
                    LOGGER.warning("Skipping invalid movie data: " + 
                        (currentMovie.title != null ? currentMovie.title : "unknown"));
                    skipped.incrementAndGet();
                }
                break;
        }
    }
    
    private void processCastElement(String qName, String value) {
        switch (qName.toLowerCase()) {
            case "f":
                currentMovieId = value;
                break;
            case "a":
                inStar = true;
                currentStar.name = value;
                break;
            case "m":
                inCast = false;
                if (isValidStar(currentStar)) {
                    // 只收集关系，不插入数据库
                    starMovieRelations.add(new Pair<>(currentMovieId, currentStar.name));
                    // 只插入明星，不插入关系
                    if (!processedStars.contains(currentStar.name)) {
                        try {
                            starQueue.put(currentStar);
                            processedStars.add(currentStar.name);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    skipped.incrementAndGet();
                }
                break;
        }
    }
    
    private void processStarElement(String qName, String value) {
        if (qName.equalsIgnoreCase("stagename")) {
            currentStar.name = value;
        } else if (qName.equalsIgnoreCase("a")) {
            inStar = false;
        }
    }
    
    private void processGenreElement(String value) {
        if (value != null && !value.isEmpty() && !processedGenres.contains(value)) {
            try {
                genreQueue.put(new GenreData(value));
                processedGenres.add(value);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            skipped.incrementAndGet();
        }
    }
    
    private boolean isValidMovie(MovieData movie) {
        if (movie == null) {
            return false;
        }
        
        // 检查必填字段
        boolean hasValidTitle = movie.title != null && !movie.title.trim().isEmpty();
        boolean hasValidDirector = movie.director != null && !movie.director.trim().isEmpty();
        boolean hasValidYear = movie.year != null && movie.year > 0 && movie.year < 2100;
        
        if (!hasValidTitle) {
            LOGGER.warning("Movie has invalid title: " + movie.title);
        }
        if (!hasValidDirector) {
            LOGGER.warning("Movie has invalid director: " + movie.director);
        }
        if (!hasValidYear) {
            LOGGER.warning("Movie has invalid year: " + movie.year);
        }
        
        return hasValidTitle && hasValidDirector && hasValidYear;
    }
    
    private boolean isValidStar(StarData star) {
        return star.name != null && !star.name.isEmpty();
    }
    
    private void processMovieBatch(List<MovieData> batch) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            
            // 检查电影是否已存在
            String checkSql = "SELECT id FROM movies WHERE title = ? AND year = ? AND director = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                for (MovieData movie : batch) {
                    checkStmt.setString(1, movie.title);
                    checkStmt.setInt(2, movie.year);
                    checkStmt.setString(3, movie.director);
                    
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            // 电影不存在，生成新ID并插入
                            String newId = generateUniqueMovieId(conn);
                            try {
                                insertMovie(conn, newId, movie);
                                movieIdCache.put(movie.id, newId);
                                moviesProcessed.incrementAndGet();
                            } catch (SQLIntegrityConstraintViolationException e) {
                                // 如果新生成的ID也冲突，尝试使用另一个ID
                                LOGGER.warning("Generated ID " + newId + " conflicts, trying alternative ID");
                                newId = generateAlternativeMovieId(conn);
                                insertMovie(conn, newId, movie);
                                movieIdCache.put(movie.id, newId);
                                moviesProcessed.incrementAndGet();
                            }
                        } else {
                            // 电影已存在，缓存其ID
                            String existingId = rs.getString("id");
                            movieIdCache.put(movie.id, existingId);
                            LOGGER.info("Movie already exists: " + movie.title + " (ID: " + existingId + ")");
                            skipped.incrementAndGet();
                        }
                    }
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error processing movie batch: " + e.getMessage(), e);
            errors.incrementAndGet();
        }
    }
    
    private String generateUniqueMovieId(Connection conn) throws SQLException {
        // 首先尝试使用 tt + 7位数字的格式
        String baseId = generateMovieId(conn);
        
        // 检查这个ID是否已经存在
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM movies WHERE id = ?")) {
            ps.setString(1, baseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return baseId;
                }
            }
        }
        
        // 如果ID已存在，生成一个带时间戳的ID
        return generateAlternativeMovieId(conn);
    }
    
    private String generateAlternativeMovieId(Connection conn) throws SQLException {
        // 使用时间戳生成唯一ID
        String timestamp = String.format("%d", System.currentTimeMillis());
        String random = String.format("%03d", new Random().nextInt(1000));
        return "tt" + timestamp.substring(timestamp.length() - 7) + random;
    }
    
    private void processStarBatch(List<StarData> batch) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            
            // 检查明星是否已存在
            String checkSql = "SELECT id FROM stars WHERE name = ?";
            String insertSql = "INSERT INTO stars (id, name, birthYear) VALUES (?, ?, ?)";
            String relationSql = "INSERT IGNORE INTO stars_in_movies (starId, movieId) VALUES (?, ?)";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                 PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                 PreparedStatement relationStmt = conn.prepareStatement(relationSql)) {
                
                for (StarData star : batch) {
                    if (star == null || star.name == null || star.name.trim().isEmpty()) {
                        LOGGER.warning("Skipping invalid star data");
                        continue;
                    }
                    
                    String starName = star.name.trim();
                    checkStmt.setString(1, starName);
                    
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        String starId;
                        if (!rs.next()) {
                            // 明星不存在，生成新ID并插入
                            starId = generateStarId(conn);
                            insertStmt.setString(1, starId);
                            insertStmt.setString(2, starName);
                            insertStmt.setObject(3, star.birthYear);
                            insertStmt.executeUpdate();
                            starIdCache.put(starName, starId);
                            starsProcessed.incrementAndGet();
                            LOGGER.fine("Inserted new star: " + starName + " (ID: " + starId + ")");
                        } else {
                            // 明星已存在，使用现有ID
                            starId = rs.getString("id");
                            starIdCache.put(starName, starId);
                            LOGGER.fine("Found existing star: " + starName + " (ID: " + starId + ")");
                        }
                        
                        // 如果有电影关联，建立关系
                        if (currentMovieId != null && movieIdCache.containsKey(currentMovieId)) {
                            String movieId = movieIdCache.get(currentMovieId);
                            if (movieId != null) {
                                relationStmt.setString(1, starId);
                                relationStmt.setString(2, movieId);
                                relationStmt.executeUpdate();
                                LOGGER.fine("Created star-movie relation: " + starName + " -> " + movieId);
                            }
                        }
                    }
                }
            }
            
            conn.commit();
            LOGGER.info("Processed star batch of size: " + batch.size());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error processing star batch: " + e.getMessage(), e);
            errors.incrementAndGet();
        }
    }
    
    private void processGenreBatch(List<GenreData> batch) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            
            // 检查类型是否已存在
            String checkSql = "SELECT id FROM genres WHERE name = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                for (GenreData genre : batch) {
                    checkStmt.setString(1, genre.name);
                    
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            // 类型不存在，生成新ID并插入
                            int newId = generateGenreId(conn);
                            insertGenre(conn, newId, genre);
                            genreIdCache.put(genre.name, newId);
                            genresProcessed.incrementAndGet();
                        } else {
                            // 类型已存在，缓存其ID
                            genreIdCache.put(genre.name, rs.getInt("id"));
                        }
                    }
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error processing genre batch: " + e.getMessage(), e);
            errors.incrementAndGet();
        }
    }
    
    private void insertStarMovieRelation(String movieId, String starId) {
        try (Connection conn = DatabaseUtil.getConnection()) {
            String sql = "INSERT IGNORE INTO stars_in_movies (starId, movieId) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, starId);
                ps.setString(2, movieId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error inserting star-movie relation: " + e.getMessage(), e);
        }
    }
    
    private String generateMovieId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CONCAT('tt', LPAD(COALESCE(MAX(SUBSTRING(id, 3)) + 1, 1), 7, '0')) " +
                "FROM movies WHERE id LIKE 'tt%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return "tt0000001";
    }
    
    private String generateStarId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CONCAT('nm', LPAD(COALESCE(MAX(SUBSTRING(id, 3)) + 1, 1), 7, '0')) " +
                "FROM stars WHERE id LIKE 'nm%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return "nm0000001";
    }
    
    private int generateGenreId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(id) + 1, 1) FROM genres")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 1;
    }
    
    private void insertMovie(Connection conn, String id, MovieData movie) throws SQLException {
        String sql = "INSERT INTO movies (id, title, year, director, price) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, movie.title);
            ps.setInt(3, movie.year);
            ps.setString(4, movie.director);
            ps.setBigDecimal(5, new java.math.BigDecimal("10.00"));
            ps.executeUpdate();
        }
    }
    
    private void insertStar(Connection conn, String id, StarData star) throws SQLException {
        String sql = "INSERT INTO stars (id, name, birthYear) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, star.name);
            ps.setObject(3, star.birthYear);
            ps.executeUpdate();
        }
    }
    
    private void insertGenre(Connection conn, int id, GenreData genre) throws SQLException {
        String sql = "INSERT INTO genres (id, name) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, genre.name);
            ps.executeUpdate();
        }
    }
    
    // 数据类
    private static class MovieData {
        String id;
        String title;
        Integer year;
        String director;
    }
    
    private static class StarData {
        String name;
        Integer birthYear;
    }
    
    private static class GenreData {
        String name;
        
        GenreData(String name) {
            this.name = name;
        }
    }
    
    // 新增：批量插入所有明星-电影关系
    private void insertAllStarMovieRelations() {
        LOGGER.info("Inserting all star-movie relations...");
        int count = 0;
        try (Connection conn = DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            String sql = "INSERT IGNORE INTO stars_in_movies (starId, movieId) VALUES (?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Pair<String, String> relation : starMovieRelations) {
                    String movieId = movieIdCache.get(relation.getKey());
                    String starId = starIdCache.get(relation.getValue());
                    if (movieId != null && starId != null) {
                        ps.setString(1, starId);
                        ps.setString(2, movieId);
                        ps.addBatch();
                        count++;
                        if (count % 1000 == 0) {
                            ps.executeBatch();
                        }
                    }
                }
                ps.executeBatch();
            }
            conn.commit();
            LOGGER.info("Inserted " + count + " star-movie relations.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error inserting star-movie relations: " + e.getMessage(), e);
        }
    }
} 