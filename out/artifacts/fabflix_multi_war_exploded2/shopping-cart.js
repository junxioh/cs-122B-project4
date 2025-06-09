// Load cart content
async function loadCart() {
    try {
        const response = await fetch("shopping-cart");

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        if (!response.ok) {
            throw new Error("Failed to fetch cart.");
        }

        const data = await response.json();
        if (data.error) {
            showError(data.error);
            return;
        }

        renderCart(data);

    } catch (error) {
        console.error("Error loading cart:", error);
        showError("Error loading cart.");
    }
}

// Render cart table
function renderCart(data) {
    const container = document.getElementById("cart-container");
    const cartItems = data.cartItems || [];
    const cartTotal = data.cartTotal || 0;

    if (data.message) {
        showMessage(data.message);
    }
    if (data.errorMessage) {
        showError(data.errorMessage);
    }

    if (cartItems.length === 0) {
        container.innerHTML = `<p class="empty-cart">Your shopping cart is empty.</p>`;
        return;
    }

    let tableHtml = `
        <table>
            <thead>
                <tr>
                    <th>Title</th>
                    <th>Price</th>
                    <th>Quantity</th>
                    <th>Remove</th>
                    <th style="text-align: right;">Item Total</th>
                </tr>
            </thead>
            <tbody>
    `;

    for (const item of cartItems) {
        tableHtml += `
            <tr>
                <td><a href="single-movie.html?id=${item.id}">${item.title}</a></td>
                <td>$${item.price.toFixed(2)}</td>
                <td class="quantity-controls">
                    <form onsubmit="return updateQuantity(event, '${item.id}')">
                        <input type="number" name="quantity" value="${item.quantity}" min="0">
                        <button type="submit" class="action-button">Update</button>
                    </form>
                </td>
                <td>
                    <form onsubmit="return removeItem(event, '${item.id}')">
                        <button type="submit" class="action-button" style="background-color: #dc3545;">X</button>
                    </form>
                </td>
                <td class="item-total" style="text-align: right;">$${item.itemTotal.toFixed(2)}</td>
            </tr>
        `;
    }

    tableHtml += `
            </tbody>
            <tfoot>
                <tr>
                    <td colspan="4" style="text-align: right; font-weight: bold;">Cart Total:</td>
                    <td class="cart-total" style="text-align: right;">$${cartTotal.toFixed(2)}</td>
                </tr>
            </tfoot>
        </table>

        <div class="cart-summary">
            <form action="payment.html" method="post">
                <button type="submit" class="action-button proceed-button">Proceed to Payment</button>
            </form>
        </div>
    `;

    container.innerHTML = tableHtml;
}

// Update quantity
async function updateQuantity(event, movieId) {
    event.preventDefault();
    const form = event.target;
    const quantity = form.quantity.value;

    const params = new URLSearchParams();
    params.append("action", "update");
    params.append("movieId", movieId);
    params.append("quantity", quantity);

    try {
        const response = await fetch("shopping-cart", {
            method: "POST",
            body: params,
        });

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        await loadCart();
    } catch (error) {
        console.error("Error updating quantity:", error);
        showError("Failed to update quantity.");
    }
}

// Remove item
async function removeItem(event, movieId) {
    event.preventDefault();

    const params = new URLSearchParams();
    params.append("action", "remove");
    params.append("movieId", movieId);

    try {
        const response = await fetch("shopping-cart", {
            method: "POST",
            body: params,
        });

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        await loadCart();
    } catch (error) {
        console.error("Error removing item:", error);
        showError("Failed to remove item.");
    }
}

// Helper functions
function showError(message) {
    const errorBox = document.getElementById("error-message");
    errorBox.textContent = message;
    errorBox.style.display = "block";
}

function showMessage(message) {
    const messageBox = document.getElementById("message");
    messageBox.textContent = message;
    messageBox.style.display = "block";
}

// Initial page load
document.addEventListener("DOMContentLoaded", loadCart);
