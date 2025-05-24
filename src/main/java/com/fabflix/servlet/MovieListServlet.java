package main.java.com.fabflix.servlet;

import main.java.com.fabflix.util.DatabaseUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;


import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 *  /movie-list —— 搜索 / 浏览 / 多重筛选 / 分页 / 排序
 */
public class MovieListServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /*============================== entry ==============================*/
    @Override
    protected void doGet(HttpServletRequest req,
                         HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try (Connection conn = DatabaseUtil.getConnection()) {

            /* ---------- 只要 browse 菜单 ---------- */
            if ("true".equals(req.getParameter("browseOptionsOnly"))) {
                resp.getWriter().write(fetchBrowseOptions(conn));
                return;
            }

            /* ---------- 解析参数 ---------- */
            QueryParams p = new QueryParams(req);

            /* ---------- WHERE & COUNT ---------- */
            SqlBuilder sb = new SqlBuilder();
            sb.appendBase().appendConditions(p);

            int total = fetchTotal(conn, sb);

            /* ---------- SELECT & ORDER & LIMIT ---------- */
            String sql = sb.buildSelect() +
                    " ORDER BY " + p.orderClause() +
                    " LIMIT ? OFFSET ?";

            List<Map<String, Object>> movies =
                    fetchMovies(conn, sql, sb.params,
                            p.pageSize, (p.currentPage - 1) * p.pageSize);

            resp.getWriter().write(jsonMovies(movies, total));

        } catch (Exception e) {
            getServletContext().log("MovieListServlet error", e);
            resp.setStatus(500);
            resp.getWriter().write("{\"error\":\"Internal Server Error\"}");
        }
    }

    /*============================== params ==============================*/
    private static class QueryParams {
        final int pageSize, currentPage;
        final String sortField;        // 允许 null 表示默认
        final String sortOrder;        // ASC / DESC / null
        final List<String> titleTokens, dirTokens, starTokens;
        final Integer searchYear;
        final String browseGenre, browseLetter;

        QueryParams(HttpServletRequest r) {
            pageSize    = clampInt(r.getParameter("pageSize"), 10, 10, 100);
            currentPage = clampInt(r.getParameter("currentPage"), 1, 1, Integer.MAX_VALUE);

            sortField = r.getParameter("sortField");       // title / rating / null
            sortOrder = r.getParameter("sortOrder");       // ASC / DESC / null

            titleTokens = tokenize(r.getParameter("searchTitle"));
            dirTokens   = tokenize(r.getParameter("searchDirector"));
            starTokens  = tokenize(r.getParameter("searchStar"));
            searchYear  = parseIntObj(r.getParameter("searchYear"));

            browseGenre  = r.getParameter("browseGenre");
            browseLetter = r.getParameter("browseLetter");
        }

        /** 生成 ORDER BY；没有排序参数时回到默认 (rating DESC, title ASC) */
        String orderClause() {
            if (sortField == null)      // 默认
                return "IFNULL(r.rating,0) DESC, m.title ASC";

            if ("title".equals(sortField))          // 按 Title 主排序
                return "m.title " + sortOrder + ", IFNULL(r.rating,0) " +
                        ("ASC".equals(sortOrder) ? "DESC" : "ASC");

            /* 否则按 rating 主排序 */
            return "IFNULL(r.rating,0) " + sortOrder + ", m.title " +
                    ("DESC".equals(sortOrder) ? "ASC" : "DESC");
        }
    }

    /*============================== SQL builder ==============================*/
    private static class SqlBuilder {
        private final StringBuilder sb = new StringBuilder();
        final List<Object> params = new ArrayList<>();

        SqlBuilder appendBase() {
            sb.append(" FROM movies m ")
                    .append("LEFT JOIN ratings r ON m.id = r.movieId ")
                    .append("LEFT JOIN stars_in_movies sim ON m.id = sim.movieId ")
                    .append("LEFT JOIN stars s ON sim.starId = s.id ");
            return this;
        }

        SqlBuilder appendConditions(QueryParams p) {
            sb.append(" WHERE 1=1 ");
            appendTokens("LOWER(m.title)",     p.titleTokens);
            appendTokens("LOWER(m.director)",  p.dirTokens);
            appendTokens("LOWER(s.name)",      p.starTokens);

            if (p.searchYear != null)
                add(" AND m.year = ?", p.searchYear);

            if (p.browseGenre != null)
                add(" AND EXISTS (SELECT 1 FROM genres_in_movies gim " +
                        "WHERE gim.movieId=m.id AND gim.genreId=?)", p.browseGenre);

            if (p.browseLetter != null) {
                if ("*".equals(p.browseLetter))
                    sb.append(" AND m.title REGEXP('^[^a-zA-Z0-9]') ");
                else
                    add(" AND m.title LIKE ?", p.browseLetter + "%");
            }
            return this;
        }

        private void appendTokens(String col, List<String> toks) {
            for (String t : toks) add(" AND " + col + " LIKE ?", "%" + t + "%");
        }

        void add(String fragment, Object v) { sb.append(fragment); params.add(v); }

        String buildSelect() {
            return "SELECT DISTINCT m.id, m.title, m.year, m.director, " +
                    "IFNULL(r.rating,0) AS rating, m.price" + sb;
        }

        String buildCount() { return "SELECT COUNT(DISTINCT m.id)" + sb; }
    }

    /*============================== DB helpers ==============================*/
    private int fetchTotal(Connection c, SqlBuilder b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(b.buildCount())) {
            setParams(ps, b.params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<Map<String,Object>> fetchMovies(Connection c, String sql,
                                                 List<Object> base,
                                                 int limit, int offset) throws SQLException {
        List<Object> all = new ArrayList<>(base);
        all.add(limit); all.add(offset);

        List<Map<String,Object>> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            setParams(ps, all);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String mid = rs.getString("id");
                    Map<String,Object> m = new HashMap<>();
                    m.put("id",       mid);
                    m.put("title",    rs.getString("title"));
                    m.put("year",     rs.getInt("year"));
                    m.put("director", rs.getString("director"));
                    m.put("rating",   rs.getDouble("rating"));
                    m.put("price",    rs.getBigDecimal("price"));
                    m.put("genres",   fetchGenres(c, mid, 3));
                    m.put("stars",    fetchStars (c, mid, 3));
                    list.add(m);
                }
            }
        }
        return list;
    }

    private List<Map<String,String>> fetchGenres(Connection c,String mid,int lim) throws SQLException{
        String q = "SELECT g.id,g.name FROM genres g " +
                "JOIN genres_in_movies gim ON g.id=gim.genreId " +
                "WHERE gim.movieId=? ORDER BY g.name LIMIT ?";
        try (PreparedStatement ps=c.prepareStatement(q)){
            ps.setString(1,mid); ps.setInt(2,lim);
            try(ResultSet rs=ps.executeQuery()){
                List<Map<String,String>> l=new ArrayList<>();
                while(rs.next()) l.add(Map.of("id",rs.getString(1),"name",rs.getString(2)));
                return l;
            }
        }
    }
    private List<Map<String,String>> fetchStars(Connection c,String mid,int lim) throws SQLException{
        String q = "SELECT s.id,s.name FROM stars s " +
                "JOIN stars_in_movies sim ON s.id=sim.starId " +
                "WHERE sim.movieId=? " +
                "ORDER BY (SELECT COUNT(*) FROM stars_in_movies WHERE starId=s.id) DESC, s.name ASC " +
                "LIMIT ?";
        try (PreparedStatement ps=c.prepareStatement(q)){
            ps.setString(1,mid); ps.setInt(2,lim);
            try(ResultSet rs=ps.executeQuery()){
                List<Map<String,String>> l=new ArrayList<>();
                while(rs.next()) l.add(Map.of("id",rs.getString(1),"name",rs.getString(2)));
                return l;
            }
        }
    }

    /*============================== browse JSON ==============================*/
    private String fetchBrowseOptions(Connection c) throws SQLException{
        // genres
        List<Map<String,String>> genres=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT id,name FROM genres ORDER BY name");
            ResultSet rs=ps.executeQuery()){
            while(rs.next()) genres.add(Map.of("id",rs.getString(1),"name",rs.getString(2)));
        }
        // letters
        Set<String> letters=new TreeSet<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT DISTINCT UPPER(LEFT(title,1)) ch FROM movies");
            ResultSet rs=ps.executeQuery()){
            while(rs.next()){
                String ch=rs.getString(1);
                if(ch==null) continue;
                if(ch.matches("[A-Z0-9]")) letters.add(ch); else letters.add("*");
            }
        }
        List<String> order=new ArrayList<>(letters);
        if(order.remove("*")) order.add("*");

        StringBuilder sb=new StringBuilder("{\"genres\":[");
        for(int i=0;i<genres.size();i++){
            var g=genres.get(i);
            sb.append("{\"id\":\"").append(g.get("id"))
                    .append("\",\"name\":\"").append(esc(g.get("name"))).append("\"}")
                    .append(i<genres.size()-1?",":"");
        }
        sb.append("],\"letters\":[");
        for(int i=0;i<order.size();i++){
            sb.append("\"").append(order.get(i)).append("\"")
                    .append(i<order.size()-1?",":"");
        }
        return sb.append("]}").toString();
    }

    /*============================== JSON helpers ==============================*/
    private String jsonMovies(List<Map<String,Object>> list,int total){
        StringBuilder js=new StringBuilder("{\"movies\":[");
        for(int i=0;i<list.size();i++){
            var m=list.get(i);
            js.append("{")
                    .append("\"id\":\"").append(m.get("id")).append("\",")
                    .append("\"title\":\"").append(esc(m.get("title"))).append("\",")
                    .append("\"year\":").append(m.get("year")).append(",")
                    .append("\"director\":\"").append(esc(m.get("director"))).append("\",")
                    .append("\"rating\":").append(m.get("rating")).append(",")
                    .append("\"price\":").append(m.get("price")).append(",")
                    .append("\"genres\":").append(jsonArr((List<?>)m.get("genres"))).append(",")
                    .append("\"stars\":").append(jsonArr((List<?>)m.get("stars")))
                    .append("}").append(i<list.size()-1?",":"");
        }
        return js.append("],\"totalMovies\":").append(total).append("}").toString();
    }
    private String jsonArr(List<?> l){
        StringBuilder sb=new StringBuilder("[");
        for(int i=0;i<l.size();i++){
            var m=(Map<?,?>)l.get(i);
            sb.append("{\"id\":\"").append(m.get("id"))
                    .append("\",\"name\":\"").append(esc(m.get("name"))).append("\"}")
                    .append(i<l.size()-1?",":"");
        }
        return sb.append("]").toString();
    }

    /*============================== misc ==============================*/
    private static void setParams(PreparedStatement ps,List<Object> vs) throws SQLException{
        for(int i=0;i<vs.size();i++) ps.setObject(i+1,vs.get(i));
    }
    private static int clampInt(String s,int def,int lo,int hi){
        try{int v=Integer.parseInt(s); return Math.max(lo,Math.min(hi,v));}
        catch(Exception e){return def;}
    }
    private static Integer parseIntObj(String s){
        try{return (s==null||s.isBlank())?null:Integer.valueOf(s.trim());}
        catch(Exception e){return null;}
    }
    private static List<String> tokenize(String s){
        if(s==null) return List.of();
        return Arrays.stream(s.trim().toLowerCase().split("\\s+"))
                .filter(t -> !t.isBlank())
                .collect(Collectors.toList());
    }
    private static String esc(Object o){
        return String.valueOf(o).replace("\\","\\\\").replace("\"","\\\"");
    }
}
