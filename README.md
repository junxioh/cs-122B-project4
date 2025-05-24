## General
- #### Team#:
  Team 60(Guinea Pork)

- #### Names:
  Junxiong Huang, Issac Zhang

- #### Project 4 Video Demo Link:
  https://www.youtube.com/watch?v=5EoeOboLbGM&t=4s

- #### Instruction of deployment:
   1. Deploy the `fabflix` WAR package to two Tomcat servers (Primary and Replica).
   2. Configure `context.xml` in `src/main/WebContent/META-INF/` with appropriate JDBC settings for each Tomcat node.
   3. Set up an Apache HTTP Server as a load balancer to forward requests from port 80 to port 8080 on the Tomcat cluster.
   4. Enable sticky sessions using `ROUTEID` cookies.
   5. Configure AWS security groups to:
      - Allow inbound traffic only on port 80 (load balancer).
      - Restrict MySQL port 3306 access to internal Tomcat instances only.

- #### Collaborations and Work Distribution:
  - **Junxiong Huang**
  - Implemented **Task 1**: Full-text Search and Autocomplete
  - Developed **Extra Credit**: Fuzzy Search using Levenshtein Distance
  - Designed and implemented core parts of **Task 2**: JDBC Connection Pooling
  - Contributed to **Task 3** and **Task 4**: Assisted with master-slave configuration and load balancer setup
  - Wrote documentation and prepared the readme
  - **Recorded the video demonstration**

- **Issac Zhang**
  - Focused on **Task 2**: JDBC backend integration and pooling refinement
  - Led configuration for **Task 3**: MySQL Master-Slave Replication
  - Built and deployed **Task 4**: Apache load balancer and Tomcat cluster setup
  - Conducted system-level testing and debugging
  - 
   All team members contributed to architecture planning, implementation, and testing.

---

## Connection Pooling

- #### Include the filename/path of all code/configuration files in GitHub of using JDBC Connection Pooling.
   - `src/main/WebContent/META-INF/context.xml`
   - `src/main/java/com/fabflix/util/DatabaseUtil.java`

- #### Explain how Connection Pooling is utilized in the Fabflix code.
  The `context.xml` file sets up a Tomcat JDBC connection pool with settings like maximum active connections, caching of prepared statements, and the database URL.  
  The `DatabaseUtil.java` class retrieves connections via JNDI (`java:comp/env/jdbc/moviedb`) and provides helper methods to acquire and release them, ensuring efficient connection reuse.

- #### Explain how Connection Pooling works with two backend SQL.
  Each Tomcat server connects to its corresponding MySQL instance (master or slave) via its own JDBC pool. The primary server uses the pool for both reads and writes (master), while the replica uses it for read-only queries (slave). Connection pooling on each node reduces overhead by maintaining a pool of active connections to their respective databases.

---

## Master/Slave

- #### Include the filename/path of all code/configuration files in GitHub of routing queries to Master/Slave SQL.
   - `src/main/WebContent/META-INF/context.xml` (configured differently for each server)
   - `src/main/java/com/fabflix/util/DatabaseUtil.java`
   - Servlet classes such as `MovieListServlet.java`, `PaymentServlet.java`

- #### How read/write requests were routed to Master/Slave SQL?
   - **Primary Tomcat (node1)** connects to the **MySQL master** and handles both read and write operations.
   - **Replica Tomcat (node2)** connects to the **MySQL slave** and handles only read operations.
   - The Apache load balancer uses sticky sessions (`ROUTEID`) to route:
      - Write and transactional requests (e.g., checkout) to **node1**
      - Read-only requests (e.g., search, browse) to **node2**


## Extra credit：
- #### Fuzzy Search Implementation Overview

In this project, we implemented multiple fuzzy search methods to enhance user experience and result accuracy.

- **SQL-Based LIKE Pattern Matching**
  - **File:** `src/main/java/com/fabflix/servlet/MovieSearchServlet.java`
  - **Description:**
    - Uses SQL `LIKE` operator: `title LIKE ?`
    - Parameter format: `%keyword%`, to match any title containing the keyword
    - Supports multi-keyword search by joining conditions with `AND`

- **Levenshtein Distance-Based Matching**
  - **File:** `src/main/java/com/fabflix/util/LevenshteinUtil.java`
  - **Description:**
    - Implements Levenshtein distance algorithm (edit distance)
    - Refines initial results from SQL in `MovieSearchServlet.java`
    - Allows a maximum edit distance of 2 (insert, delete, substitute)
    - Uses dynamic programming for efficient calculation

- **Full-Text Search Optimization**
  - **File:** `src/main/java/com/fabflix/servlet/MovieFullTextSearchServlet.java`
  - **Description:**
    - Advanced title matching logic
    - Supports word-boundary matching: keywords at start of title or title words
    - Sorts results by rating and title for better relevance

