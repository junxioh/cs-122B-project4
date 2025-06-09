document.getElementById("login-form").addEventListener("submit", async function(event) {
    // Prevent default form submission
    event.preventDefault();

    const form = document.getElementById("login-form");
    form.classList.add("loading");

    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value.trim();
    const errorMessageDiv = document.getElementById("error-message");
    // const recaptchaResponse = grecaptcha.getResponse();

    try {
        const response = await fetch("/fabflix-login/api/login", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body: `email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`
            // body: `email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}&g-recaptcha-response=${encodeURIComponent(recaptchaResponse)}`
        });

        const result = await response.json();

        if (result.status === "success") {
            // Store the JWT token in a cookie
            document.cookie = `jwt=${result.token}; path=/; max-age=86400; SameSite=Strict`;
            
            // Redirect to movie list page
            window.location.href = "/fabflix-movies/movie-list.html";
        } else {
            errorMessageDiv.textContent = result.message || "Login failed.";
            // 重置reCAPTCHA
            // grecaptcha.reset();
        }
    } catch (error) {
        console.error("Error during login:", error);
        errorMessageDiv.textContent = "Network error. Please try again later.";
        // 重置reCAPTCHA
        // grecaptcha.reset();
    } finally {
        form.classList.remove("loading");
    }
});
