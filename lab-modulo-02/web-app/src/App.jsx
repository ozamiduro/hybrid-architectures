import { useEffect, useState } from "react";

const PRODUCT_API = import.meta.env.VITE_PRODUCT_API_URL || "http://localhost:8081";
const ORDER_API = import.meta.env.VITE_ORDER_API_URL || "http://localhost:8082";

export default function App() {
  const [products, setProducts] = useState([]);
  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [message, setMessage] = useState("");

  async function loadProducts() {
    const response = await fetch(`${PRODUCT_API}/api/products`);
    setProducts(await response.json());
  }

  useEffect(() => {
    loadProducts().catch(() => setMessage("No se pudo conectar con product-service"));
  }, []);

  async function createOrder(event) {
    event.preventDefault();
    setMessage("");

    const response = await fetch(`${ORDER_API}/api/orders`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        productId: Number(productId),
        quantity: Number(quantity)
      })
    });

    if (response.ok) {
      const order = await response.json();
      setMessage(`Orden #${order.id} creada. Total: S/ ${order.total}`);
    } else {
      setMessage(await response.text());
    }
  }

  return (
    <main className="container">
      <h1>TechStore</h1>
      <p>Demo de arquitectura con 2 microservicios, React y PostgreSQL.</p>

      <section>
        <h2>Productos</h2>
        <ul>
          {products.map((product) => (
            <li key={product.id}>
              <strong>{product.name}</strong> — S/ {product.price} — stock: {product.stock}
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2>Crear orden</h2>
        <form onSubmit={createOrder}>
          <select value={productId} onChange={(e) => setProductId(e.target.value)} required>
            <option value="">Seleccione un producto</option>
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name}
              </option>
            ))}
          </select>

          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />

          <button type="submit">Comprar</button>
        </form>
      </section>

      {message && <p className="message">{message}</p>}
    </main>
  );
}
