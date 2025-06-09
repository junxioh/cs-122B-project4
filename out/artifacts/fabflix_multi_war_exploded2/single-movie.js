// Helper to get URL parameters
function getUrlParameter(name) {
    const params = new URLSearchParams(window.location.search);
    return params.get(name);
}

// Display Add to Cart Notification
function showNotification(message) {
    const notification = document.getElementById('cart-notification');
    const messageSpan = document.getElementById('cart-message');
    messageSpan.textContent = message;
    notification.style.display = 'block';

    setTimeout(hideNotification, 3000);
}

// Hide notification
function hideNotification() {
    const notification = document.getElementById('cart-notification');
    notification.style.display = 'none';
}

// Display error message
function showError(message) {
    const errorBox = document.getElementById('error-message');
    errorBox.textContent = message;
    errorBox.style.display = 'block';
}

// Load movie details
async function loadMovie() {
    const movieId = getUrlParameter("id");
    if (!movieId) {
        showError("Movie ID is missing.");
        return;
    }

    try {
        const response = await fetch(`single-movie?id=${movieId}`);

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        if (!response.ok) {
            throw new Error("Failed to fetch movie.");
        }

        const data = await response.json();
        if (data.error) {
            showError(data.error);
            return;
        }

        renderMovie(data.movie);

        // Handle add-to-cart notification
        const cartMessage = getUrlParameter("cartMessage");
        if (cartMessage) {
            showNotification(decodeURIComponent(cartMessage));
            // Clean URL (only keep id parameter)
            const newUrl = window.location.origin + window.location.pathname + `?id=${movieId}`;
            window.history.replaceState({}, document.title, newUrl);
        }

    } catch (error) {
        console.error("Error loading movie details:", error);
        showError("Error loading movie details.");
    }
}

// Render movie info
function renderMovie(movie) {
    const container = document.getElementById("movie-info");
    container.innerHTML = `
        <h2>${movie.title} (${movie.year})</h2>
        <p><strong>Director:</strong> ${movie.director}</p>
        <p><strong>Price:</strong> $${parseFloat(movie.price).toFixed(2)}</p>
        <p><strong>Rating:</strong> ${movie.rating}</p>
        <p><strong>Genres:</strong> ${movie.genres.map(g => `<a href="movie-list.html?browseGenre=${g.id}">${g.name}</a>`).join(", ")}</p>
        <p><strong>Stars:</strong> ${movie.stars.map(s => `<a href="single-star.html?id=${s.id}">${s.name}</a>`).join(", ")}</p>

        <div style="margin-top:20px;">
            <form action="shopping-cart" method="post">
                <input type="hidden" name="action" value="add">
                <input type="hidden" name="movieId" value="${movie.id}">
                <input type="hidden" name="movieTitle" value="${movie.title}">
                <button type="submit" class="action-button">Add to Cart</button>
            </form>
        </div>
    `;
}

// Initialize when page loads
document.addEventListener("DOMContentLoaded", loadMovie);
