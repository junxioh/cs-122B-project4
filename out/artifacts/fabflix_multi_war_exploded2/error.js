
document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(window.location.search);
    const msg = params.get("msg");
    if (msg) {
        const detail = document.getElementById("error-detail");
        detail.textContent = "Details: " + decodeURIComponent(msg);
        detail.style.display = "block";
    }
});
