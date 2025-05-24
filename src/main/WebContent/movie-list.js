/* --------------------------------------------------
 * movie-list.js – 多重筛选 + 分页 + 三段式排序
 * build 2025-04-28 22:00  (JDK-11 friendly)
 * -------------------------------------------------- */
console.log("MULTI-FILTER 2025-04-28 22:00");

const state = {
    currentPage : 1,
    pageSize    : 10,
    sortField   : null,        // null = 默认 (rating DESC)
    sortOrder   : null,        // ASC / DESC / null

    searchTitle : "", searchYear : "", searchDirector : "", searchStar : "",
    browseGenre : null,
    browseLetter: null
};

// 自动补全缓存
const autocompleteCache = {};

// 添加全局缓存对象
let movieCache = {};

/* ========== 把 state 转 URLSearchParams ========== */
const buildParams = () =>{
    const p = new URLSearchParams();
    Object.entries(state).forEach(([k,v])=>{
        if(v!==null && v!=="") p.append(k,v);
    });
    return p;
};

/* ========== 拉取 + 渲染 ========== */
async function loadMovies(){
    const r = await fetch(`./movie-list?${buildParams()}`);
    if(r.status===401) return location.href="login.html";
    const d = await r.json();
    renderTable(d.movies);
    renderPager(d.totalMovies);
}

const tbody = document.querySelector("#movies-table tbody");
function renderTable(arr){
    tbody.innerHTML = arr.map(m=>`
    <tr>
      <td><a href="single-movie.html?id=${m.id}">${m.title}</a></td>
      <td>${m.year}</td>
      <td>${m.director}</td>
      <td>${m.genres.map(g=>g.name).join(", ")}</td>
      <td>${m.stars.map(s=>`<a href="single-star.html?id=${s.id}">${s.name}</a>`).join(", ")}</td>
      <td>${m.rating}</td>
      <td>$${(+m.price).toFixed(2)}</td>
    </tr>`).join("");
}

function renderPager(total){
    const pages = Math.max(1, Math.ceil(total/state.pageSize));
    pageInfo.textContent = `Page ${state.currentPage} of ${pages}`;
    prevBtn.disabled = state.currentPage<=1;
    nextBtn.disabled = state.currentPage>=pages;
}

/* ---------- DOM 缓存 ---------- */
const form       = document.getElementById("search-form");
const prevBtn    = document.getElementById("prev-page");
const nextBtn    = document.getElementById("next-page");
const pageInfo   = document.getElementById("page-info");
const pageSizeSel= document.getElementById("pageSize");
const genreBox   = document.getElementById("genre-links");
const letterBox  = document.getElementById("letter-links");
const resetAllBtn= document.getElementById("clear-search");
const searchTitle= document.getElementById("searchTitle");

/* ================= 事件绑定 ================= */

/* —— 搜索 —— */
form.addEventListener("submit",e=>{
    e.preventDefault();
    const searchTitleVal = form.searchTitle.value.trim();

    // 判断是否只进行标题搜索，还是多条件搜索
    if (searchTitleVal &&
        !form.searchYear.value.trim() &&
        !form.searchDirector.value.trim() &&
        !form.searchStar.value.trim()) {

        // 使用新实现的基于单词前缀的搜索 - 使用相同的API接口
        console.log("Search using word prefixes: " + searchTitleVal);

        // 显示加载指示器
        tbody.innerHTML = "<tr><td colspan='7' style='text-align:center;padding:30px;'>搜索中，请稍候...</td></tr>";

        fetch(`api/movie-search?query=${encodeURIComponent(searchTitleVal)}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('network response error，status: ' + response.status);
                }
                return response.json();
            })
            .then(data => {
                console.log("search result: ", data);

                // 如果只返回了标题和ID，则需要获取完整的电影信息
                if (data.length > 0 && !data[0].hasOwnProperty('year')) {
                    // 获取完整的电影详情
                    const ids = data.map(movie => movie.id);
                    return fetch(`api/movies-by-ids?ids=${encodeURIComponent(JSON.stringify(ids))}`)
                        .then(response => {
                            if (!response.ok) {
                                // 如果获取详情失败，至少显示我们已经有的结果
                                console.error("Failed to obtain movie details, only display title");

                                // 为方便显示，创建简化的电影对象
                                return data.map(movie => ({
                                    id: movie.id,
                                    title: movie.title,
                                    year: "unknown",
                                    director: "unknown",
                                    genres: [],
                                    stars: [],
                                    rating: 0,
                                    price: 0
                                }));
                            }
                            return response.json();
                        });
                }

                // 如果已经是完整的电影信息，直接返回
                return data;
            })
            .then(movies => {
                // 渲染电影列表
                if (movies.length === 0) {
                    tbody.innerHTML = "<tr><td colspan='7' style='text-align:center;padding:30px;'>No matching movie found</td></tr>";
                } else {
                    renderTable(movies);
                    renderPager(movies.length);
                }
            })
            .catch(error => {
                console.error("search error:", error);
                tbody.innerHTML = "<tr><td colspan='7' style='text-align:center;padding:30px;'>Search error, please try again</td></tr>";

                // 如果搜索出错，回退到常规搜索
                performRegularSearch();
            });
    } else if (searchTitleVal && searchTitleVal.includes(" ")) {
        // 如果有多个关键词且与其他条件一起搜索，使用全文搜索
        console.log("Use full-text search: " + searchTitleVal);
        fetch(`api/movie-full-search?query=${encodeURIComponent(searchTitleVal)}`)
            .then(response => response.json())
            .then(data => {
                // 直接渲染搜索结果
                if (data.length === 0) {
                    tbody.innerHTML = "<tr><td colspan='7' style='text-align:center;padding:30px;'>No matching movie found</td></tr>";
                } else {
                    renderTable(data);
                    renderPager(data.length);
                }
            })
            .catch(error => {
                console.error("search error:", error);
                // 如果全文搜索失败，回退到常规搜索
                performRegularSearch();
            });
    } else {
        // 使用常规多条件搜索
        performRegularSearch();
    }
});

function performRegularSearch() {
    Object.assign(state,{
        searchTitle    : form.searchTitle.value.trim(),
        searchYear     : form.searchYear.value.trim(),
        searchDirector : form.searchDirector.value.trim(),
        searchStar     : form.searchStar.value.trim(),
        currentPage    : 1
    });
    loadMovies();
}

/* —— Reset All：搜索+浏览+排序 全部清掉 —— */
resetAllBtn.onclick = () =>{
    form.reset();
    Object.assign(state,{
        searchTitle:"",searchYear:"",searchDirector:"",searchStar:"",
        browseGenre:null,browseLetter:null,
        sortField:null, sortOrder:null,
        currentPage:1
    });
    highlight(genreBox,null);
    highlight(letterBox,null);
    paintHeaderArrows();
    loadMovies();
};

/* —— 分页 —— */
prevBtn.onclick = ()=>{ if(state.currentPage>1){state.currentPage--;loadMovies();}};
nextBtn.onclick = ()=>{ state.currentPage++;loadMovies();};
pageSizeSel.onchange = function(){
    state.pageSize = +this.value; state.currentPage=1; loadMovies();
};

/* —— 三段式排序 —— */
document.getElementById("title-header").onclick  = ()=>cycleSort("title");
document.getElementById("rating-header").onclick = ()=>cycleSort("rating");

function cycleSort(field){
    if(state.sortField!==field){                   // 第一次点：按 field ASC
        state.sortField=field; state.sortOrder="ASC";
    }else if(state.sortOrder==="ASC"){             // 第二次：DESC
        state.sortOrder="DESC";
    }else{                                         // 第三次：回默认
        state.sortField=state.sortOrder=null;
    }
    state.currentPage=1;
    paintHeaderArrows();
    loadMovies();
}
function paintHeaderArrows(){
    const t=document.getElementById("title-header");
    const r=document.getElementById("rating-header");
    t.innerHTML="Title"; r.innerHTML="Rating";
    if(state.sortField){
        const el = state.sortField==="title"?t:r;
        el.innerHTML += state.sortOrder==="ASC"?" ▲":" ▼";
    }
}

/* —— 加载 browse 菜单 —— */
async function loadBrowseOptions(){
    const r = await fetch("./movie-list?browseOptionsOnly=true");
    if(r.status===401) return location.href="login.html";
    const d = await r.json();

    /* genre */
    genreBox.innerHTML = "<strong>By Genre:</strong> ";
    d.genres.forEach(g=>{
        const a=document.createElement("a"); a.href="#";
        a.textContent=g.name; a.dataset.id=g.id;
        genreBox.appendChild(a);
    });

    /* letter */
    letterBox.innerHTML = "<strong>By Title Letter:</strong> ";
    d.letters.forEach(l=>{
        const a=document.createElement("a"); a.href="#";
        a.textContent=l; a.dataset.letter=l;
        letterBox.appendChild(a);
    });
}

/* —— 选择 / 取消 genre —— */
genreBox.onclick = e=>{
    if(e.target.tagName!=="A") return;
    const id = e.target.dataset.id;
    state.browseGenre = (state.browseGenre===id) ? null : id;   // 再点同一个 = 取消
    state.currentPage=1;
    highlight(genreBox,state.browseGenre?"[data-id='"+state.browseGenre+"']":null);
    loadMovies();
};
/* —— 选择 / 取消 letter —— */
letterBox.onclick = e=>{
    if(e.target.tagName!=="A") return;
    const lt = e.target.dataset.letter;
    state.browseLetter = (state.browseLetter===lt) ? null : lt;
    state.currentPage=1;
    highlight(letterBox,state.browseLetter?"[data-letter='"+state.browseLetter+"']":null);
    loadMovies();
};

/* —— helper：加 / 去 selected class —— */
function highlight(container, selector){
    container.querySelectorAll("a").forEach(a=>a.classList.remove("selected"));
    if(selector) container.querySelector(selector)?.classList.add("selected");
}

/* ================= 自动补全功能 ================= */
/*
 * 处理自动补全查询函数
 * query: 用户输入的查询字符串
 * doneCallback: 自动补全库提供的回调函数，用于接收结果
 */
function handleMovieLookup(query, doneCallback) {
    console.log("Movie auto completion query triggered：" + query);

    // 检查缓存中是否有结果
    if (movieCache[query]) {
        console.log("cache");
        doneCallback({ suggestions: movieCache[query] });
        return;
    }

    // 发送AJAX请求到后端
    console.log("send request：api/movie-search?query=" + query);
    $.ajax({
        method: "GET",
        url: "api/movie-search?query=" + escape(query),
        success: function(data) {
            console.log("success");
            handleLookupSuccess(data, query, doneCallback);
        },
        error: function(xhr, status, error) {
            console.error("error:", error);
            console.error("status:", status);
            console.error("response:", xhr.responseText);
            doneCallback({ suggestions: [] });
        }
    });
}

/*
 * 处理自动补全查询成功
 */
function handleLookupSuccess(data, query, doneCallback) {
    console.log("response:", typeof data);

    // 如果是字符串，需要解析为JSON
    let jsonData = typeof data === 'string' ? JSON.parse(data) : data;
    console.log("data:", jsonData);

    // 转换数据格式为自动补全库所需格式
    let suggestions = $.map(jsonData, function(item) {
        return { value: item.title, data: item.id };
    });

    // 将结果存入缓存
    movieCache[query] = suggestions;

    // 调用回调函数，传递建议列表
    doneCallback({ suggestions: suggestions });
}

/*
 * 处理选中建议项的函数
 */
function handleSelectMovie(suggestion) {
    console.log("select movie: " + suggestion.value + " (ID: " + suggestion.data + ")");
    window.location.href = "single-movie.html?id=" + encodeURIComponent(suggestion.data);
}

/*
 * 常规搜索函数（当按回车或点击搜索按钮时触发）
 */
function handleNormalSearch() {
    console.log("convention search");
    // 现有的搜索表单提交逻辑已经在form.addEventListener("submit")中实现
    $("#search-form").submit();
}

/* ================= init ================= */
document.addEventListener("DOMContentLoaded",()=>{
    // 初始化jQuery自动补全
    $("#searchTitle").autocomplete({
        lookup: function(query, doneCallback) {
            handleMovieLookup(query, doneCallback);
        },
        onSelect: function(suggestion) {
            handleSelectMovie(suggestion);
        },
        minChars: 3,
        deferRequestBy: 300,
        appendTo: $(".title-group"),
        forceFixPosition: true,
        showNoSuggestionNotice: true,
        noSuggestionNotice: "未找到匹配的电影"
    });

    console.log("Auto completion initialization completed");

    loadBrowseOptions().then(()=>{ paintHeaderArrows(); loadMovies(); });
});

// 新增API：根据ID批量获取电影详情
// 如果后端没有实现这个API，我们也可以使用单个ID查询，但效率会较低
function fetchMoviesByIds(ids) {
    return fetch(`api/movies-by-ids?ids=${encodeURIComponent(JSON.stringify(ids))}`)
        .then(response => response.json());
}
