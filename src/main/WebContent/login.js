document.getElementById("login-form").addEventListener("submit", async function(event) {
    // 阻止表单默认提交行为
    event.preventDefault();

    const email = document.getElementById("email").value.trim();
    const password = document.getElementById("password").value.trim();
    const errorMessageDiv = document.getElementById("error-message");
    const recaptchaResponse = grecaptcha.getResponse();

    try {
        const response = await fetch("./login", {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
            },
            body: `email=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}&g-recaptcha-response=${encodeURIComponent(recaptchaResponse)}`
        });

        const result = await response.json();

        if (result.status === "success") {
            window.location.href = "movie-list.html";
            // 登录成功后跳转到首页
            // window.location.href = "./index.html";
        } else {
            errorMessageDiv.textContent = result.message || "Login failed.";
            // 重置reCAPTCHA
            grecaptcha.reset();
        }
    } catch (error) {
        console.error("Error during login:", error);
        errorMessageDiv.textContent = "Network error. Please try again later.";
        // 重置reCAPTCHA
        grecaptcha.reset();
    }
});
