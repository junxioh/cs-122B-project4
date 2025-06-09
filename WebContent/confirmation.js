document.addEventListener("DOMContentLoaded", async function () {
    const container = document.getElementById("confirmation-content");

    try {
        const response = await fetch("api/confirmation");

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        if (!response.ok) {
            throw new Error("Failed to fetch confirmation data.");
        }

        const data = await response.json();

        if (data.status === "success") {
            const itemsHtml = data.confirmedItems.map(item => `
                <tr>
                    <td>${item.title}</td>
                    <td>$${parseFloat(item.price).toFixed(2)}</td>
                    <td>${item.quantity}</td>
                    <td>$${parseFloat(item.itemTotal).toFixed(2)}</td>
                </tr>
            `).join("");

            container.innerHTML = `
                <h1>Order Confirmed!</h1>
                ${data.saleDate ? `<p style="text-align:center;">Order Date: ${data.saleDate}</p>` : ''}
                <table class="order-table">
                    <thead>
                        <tr>
                            <th>Movie Title</th>
                            <th>Price</th>
                            <th>Quantity</th>
                            <th>Total</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${itemsHtml}
                    </tbody>
                </table>
                <div class="order-total">
                    Total Charged: $${parseFloat(data.finalTotal).toFixed(2)}
                </div>
                <div class="action-buttons">
                    <a href="movie-list.html">Back to Movie List</a>
                    <a href="logout">Logout</a>
                </div>
            `;
        } else {
            container.innerHTML = `<div class="error-message">${data.message}</div>`;
        }
    } catch (error) {
        console.error("Error fetching confirmation data:", error);
        container.innerHTML = `<div class="error-message">Failed to load order confirmation. Please try again later.</div>`;
    }
});
