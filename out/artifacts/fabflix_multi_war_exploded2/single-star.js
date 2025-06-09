// Helper to get URL parameters
function getUrlParameter(name) {
    const params = new URLSearchParams(window.location.search);
    return params.get(name);
}

// Display error message
function showError(message) {
    const errorBox = document.getElementById('error-message');
    errorBox.textContent = message;
    errorBox.style.display = 'block';
}

// Load star details
async function loadStar() {
    const starId = getUrlParameter("id");
    if (!starId) {
        showError("Star ID is missing.");
        return;
    }

    try {
        const response = await fetch(`single-star?id=${starId}`);

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        if (!response.ok) {
            throw new Error("Failed to fetch star.");
        }

        const data = await response.json();
        if (data.error) {
            showError(data.error);
            return;
        }

        renderStar(data.star);

    } catch (error) {
        console.error("Error loading star details:", error);
        showError("Error loading star details.");
    }
}

// Render star details
function renderStar(star) {
    const container = document.getElementById("star-info");
    const moviesHtml = star.movies.length > 0
        ? `<ul>${star.movies.map(m => `<li><a href="single-movie.html?id=${m.id}">${m.title}</a> (${m.year})</li>`).join('')}</ul>`
        : "<p>No movies found for this star.</p>";

    container.innerHTML = `
        <h2>${star.name}</h2>
        <p><strong>Born:</strong> ${star.birthYear ? star.birthYear : 'Unknown'}</p>
        <div class="movies-list">
            <h3>Movies Starred In:</h3>
            ${moviesHtml}
        </div>
    `;
}

// Initialize when page loads
document.addEventListener("DOMContentLoaded", loadStar);
