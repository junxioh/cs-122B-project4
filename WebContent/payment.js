// Fetch the cart total on page load
async function fetchCartTotal() {
    try {
        const response = await fetch("shopping-cart");

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        if (!response.ok) {
            throw new Error("Failed to fetch cart total.");
        }

        const data = await response.json();
        const totalDisplay = document.getElementById("total-display");

        if (data.cartTotal !== undefined) {
            totalDisplay.textContent = `Order Total: $${parseFloat(data.cartTotal).toFixed(2)}`;
        } else {
            totalDisplay.textContent = "Order Total: $0.00";
        }
    } catch (error) {
        console.error("Error fetching cart total:", error);
        document.getElementById("total-display").textContent = "Order Total: Error loading.";
    }
}

// Submit payment form
async function submitPayment(event) {
    event.preventDefault();

    const form = document.getElementById("payment-form");
    const formData = new FormData(form);
    const params = new URLSearchParams(formData);

    try {
        const response = await fetch("payment", {
            method: "POST",
            body: params
        });

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        if (!response.ok) {
            throw new Error("Failed to process payment.");
        }

        const data = await response.json();

        if (data.success) {
            window.location.href = "confirmation.html";
        } else {
            showError(data.errorMessage || "Payment failed.");
        }
    } catch (error) {
        console.error("Error submitting payment:", error);
        showError("Payment request failed.");
    }
}

// Helper function to show error
function showError(message) {
    const errorBox = document.getElementById("error-message");
    errorBox.textContent = message;
    errorBox.style.display = "block";
}

document.addEventListener("DOMContentLoaded", () => {
    fetchCartTotal();
    document.getElementById("payment-form").addEventListener("submit", submitPayment);
});
