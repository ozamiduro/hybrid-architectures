import { useEffect, useState } from 'react';

const P = import.meta.env.VITE_PRODUCT_API_URL || '/api/products';
const O = import.meta.env.VITE_ORDER_API_URL || '/api/orders';

async function getJson(url) {
  const response = await fetch(url, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  return response.json();
}

export default function App() {
  const [products, setProducts] = useState([]);
  const [orders, setOrders] = useState([]);
  const [qty, setQty] = useState({});
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function loadProducts() {
    const data = await getJson(P);
    setProducts(data);
    return data;
  }

  async function loadOrders() {
    const data = await getJson(O);
    setOrders(data);
    return data;
  }

  async function load() {
    try {
      await Promise.all([loadProducts(), loadOrders()]);
    } catch (e) {
      setMessage(`Error al cargar: ${e.message}`);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function order(product) {
    const quantity = Number(qty[product.id] || 1);

    if (!Number.isInteger(quantity) || quantity < 1) {
      setMessage('La cantidad debe ser un entero positivo.');
      return;
    }

    if (quantity > product.stock) {
      setMessage(`Stock insuficiente. Solo hay ${product.stock} unidades disponibles.`);
      return;
    }

    setBusy(true);
    setMessage('Creando orden...');

    try {
      const response = await fetch(O, {
        method: 'POST',
        cache: 'no-store',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId: product.id, quantity })
      });

      const body = await response.json().catch(() => ({}));

      if (!response.ok) {
        throw new Error(body.message || body.detail || `HTTP ${response.status}`);
      }

      // Consultamos explícitamente el producto afectado para mostrar el stock
      // confirmado por product-service, evitando valores antiguos del navegador.
      const updatedProduct = await getJson(`${P}/${product.id}`);
      setProducts(current =>
        current.map(item => item.id === updatedProduct.id ? updatedProduct : item)
      );

      // Actualizamos también la lista de órdenes.
      await loadOrders();

      setMessage(
        `Orden #${body.id} creada. Se descontaron ${quantity} unidades de ${updatedProduct.name}. Stock actual: ${updatedProduct.stock}.`
      );
    } catch (e) {
      setMessage(`No se pudo crear la orden: ${e.message}`);
      // Si la operación falló, volvemos a consultar para mostrar el estado real.
      await load().catch(() => {});
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="wrap">
      <header>
        <div>
          <p className="eyebrow">LABORATORIO KUBERNETES · JAVA 21 · REACT</p>
          <h1>TechStore</h1>
          <p className="muted">Demo de órdenes e inventario</p>
        </div>
        <button className="secondary" onClick={load} disabled={busy}>Actualizar</button>
      </header>

      {message && <p className="notice">{message}</p>}

      <h2>Productos</h2>
      <div className="grid">
        {products.map(product => (
          <article className="card" key={product.id}>
            <small>Producto #{product.id}</small>
            <h3>{product.name}</h3>
            <p className="price">S/ {Number(product.price).toFixed(2)}</p>
            <p className="stock">Stock disponible: <b>{product.stock}</b></p>
            <div className="actions">
              <label>
                Cantidad
                <input
                  type="number"
                  min="1"
                  max={product.stock}
                  value={qty[product.id] || 1}
                  onChange={e => setQty({ ...qty, [product.id]: e.target.value })}
                />
              </label>
              <button disabled={busy || product.stock < 1} onClick={() => order(product)}>
                {product.stock < 1 ? 'Sin stock' : 'Crear orden'}
              </button>
            </div>
          </article>
        ))}
      </div>

      <h2>Órdenes</h2>
      {orders.length === 0 ? (
        <p className="empty">Todavía no hay órdenes.</p>
      ) : (
        <div className="tablewrap">
          <table>
            <thead>
              <tr><th>ID</th><th>Producto</th><th>Cantidad</th><th>Precio unitario</th><th>Total</th><th>Fecha</th></tr>
            </thead>
            <tbody>
              {[...orders].reverse().map(o => (
                <tr key={o.id}>
                  <td>#{o.id}</td>
                  <td>#{o.productId}</td>
                  <td>{o.quantity}</td>
                  <td>S/ {Number(o.unitPrice).toFixed(2)}</td>
                  <td>S/ {Number(o.total).toFixed(2)}</td>
                  <td>{new Date(o.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <footer>TechStore · Laboratorio educativo de Kubernetes.</footer>
    </main>
  );
}
