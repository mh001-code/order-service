/**
 * Load test — Order Processing System
 *
 * Pré-requisitos:
 *   - k6 instalado: https://k6.io/docs/getting-started/installation/
 *   - Todos os serviços rodando (docker-compose up)
 *
 * Como rodar:
 *   k6 run k6/load-test.js
 *
 * Com relatório HTML (requer k6 + xk6-dashboard ou --out):
 *   k6 run --out json=k6/results.json k6/load-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─── Configuração ─────────────────────────────────────────────────────────────

const ORDER_SERVICE_URL  = __ENV.ORDER_URL  || 'http://localhost:8085';
const INVENTORY_SERVICE_URL = __ENV.INVENTORY_URL || 'http://localhost:8081';

export const options = {
  stages: [
    { duration: '30s', target: 10 },  // ramp-up: 0 → 10 usuários em 30s
    { duration: '1m',  target: 10 },  // sustentado: 10 usuários por 1 minuto
    { duration: '30s', target: 25 },  // pico: até 25 usuários
    { duration: '30s', target: 0  },  // ramp-down
  ],
  thresholds: {
    http_req_failed:                    ['rate<0.01'],   // < 1% de erros
    http_req_duration:                  ['p(95)<500'],   // p95 < 500ms
    'order_creation_duration':          ['p(95)<400'],   // criação de pedido p95 < 400ms
    'order_cancellation_duration':      ['p(95)<300'],
  },
};

// ─── Métricas customizadas ────────────────────────────────────────────────────

const ordersCreated    = new Counter('orders_created');
const ordersCancelled  = new Counter('orders_cancelled');
const ordersFailed     = new Counter('orders_failed');
const createDuration   = new Trend('order_creation_duration', true);
const cancelDuration   = new Trend('order_cancellation_duration', true);

// ─── Setup: cria produtos no inventory-service ────────────────────────────────

export function setup() {
  const headers = { 'Content-Type': 'application/json' };

  const products = [
    { name: 'Notebook Pro',    sku: 'NTB-001', description: 'Notebook 16GB RAM', unitPrice: 4999.90, initialStock: 500 },
    { name: 'Mouse Gamer',     sku: 'MSE-001', description: 'Mouse 12000 DPI',    unitPrice: 299.90,  initialStock: 1000 },
    { name: 'Teclado Mecânico',sku: 'KBD-001', description: 'Switch Red',         unitPrice: 599.90,  initialStock: 800 },
    { name: 'Monitor 27"',     sku: 'MON-001', description: '4K IPS 144Hz',       unitPrice: 2499.90, initialStock: 300 },
    { name: 'SSD 1TB',         sku: 'SSD-001', description: 'NVMe Gen4',          unitPrice: 499.90,  initialStock: 1000 },
  ];

  const createdProducts = [];

  for (const product of products) {
    const res = http.post(
      `${INVENTORY_SERVICE_URL}/products`,
      JSON.stringify(product),
      { headers }
    );

    if (res.status === 201) {
      createdProducts.push(res.json());
      console.log(`✓ Produto criado: ${product.name} (id: ${res.json().id})`);
    } else {
      console.warn(`✗ Falha ao criar produto ${product.sku}: ${res.status} ${res.body}`);
    }
  }

  if (createdProducts.length === 0) {
    throw new Error('Nenhum produto criado — verifique se inventory-service está rodando em ' + INVENTORY_SERVICE_URL);
  }

  return { products: createdProducts };
}

// ─── Cenário principal ────────────────────────────────────────────────────────

export default function (data) {
  const headers = { 'Content-Type': 'application/json' };
  const products = data.products;

  // Seleciona 1–3 produtos aleatórios para o pedido
  const itemCount = Math.floor(Math.random() * 3) + 1;
  const selectedProducts = shuffle(products).slice(0, itemCount);

  const customerId = randomUUID();
  const items = selectedProducts.map(p => ({
    productId:   p.id,
    productName: p.name,
    quantity:    Math.floor(Math.random() * 3) + 1,
    unitPrice:   p.unitPrice,
  }));

  // ── Criar pedido ──────────────────────────────────────────────────────────

  const createStart = Date.now();
  const createRes = http.post(
    `${ORDER_SERVICE_URL}/orders`,
    JSON.stringify({ customerId, items }),
    { headers }
  );
  createDuration.add(Date.now() - createStart);

  const created = check(createRes, {
    'order created — status 201':      (r) => r.status === 201,
    'order has id':                    (r) => r.json('id') !== undefined,
    'order status is CONFIRMED':       (r) => r.json('status') === 'CONFIRMED',
  });

  if (!created) {
    ordersFailed.add(1);
    console.error(`Falha ao criar pedido: ${createRes.status} — ${createRes.body}`);
    sleep(1);
    return;
  }

  ordersCreated.add(1);
  const orderId = createRes.json('id');

  sleep(0.5);

  // ── Consultar pedido ──────────────────────────────────────────────────────

  const getRes = http.get(`${ORDER_SERVICE_URL}/orders/${orderId}`);
  check(getRes, {
    'get order — status 200':   (r) => r.status === 200,
    'order id matches':         (r) => r.json('id') === orderId,
  });

  sleep(0.5);

  // ── Cancelar ~30% dos pedidos ─────────────────────────────────────────────

  if (Math.random() < 0.30) {
    const cancelStart = Date.now();
    const cancelRes = http.patch(`${ORDER_SERVICE_URL}/orders/${orderId}/cancel`, null, { headers });
    cancelDuration.add(Date.now() - cancelStart);

    const cancelled = check(cancelRes, {
      'order cancelled — status 200':     (r) => r.status === 200,
      'order status is CANCELLED':        (r) => r.json('status') === 'CANCELLED',
    });

    if (cancelled) ordersCancelled.add(1);
  }

  // ── Listar pedidos do cliente ─────────────────────────────────────────────

  const listRes = http.get(`${ORDER_SERVICE_URL}/orders?customerId=${customerId}`);
  check(listRes, {
    'list orders — status 200': (r) => r.status === 200,
  });

  sleep(Math.random() * 1 + 0.5); // 0.5–1.5s entre iterações
}

// ─── Teardown: exibe resumo ───────────────────────────────────────────────────

export function teardown(data) {
  console.log('\n══════════════════════════════════════════════');
  console.log('   Resumo do Load Test — Order Processing System');
  console.log('══════════════════════════════════════════════');
  console.log(`Produtos disponíveis no estoque: ${data.products.length}`);
  console.log('Verifique as métricas detalhadas no output acima.');
  console.log('Grafana: http://localhost:3000  (admin/admin)');
  console.log('Zipkin:  http://localhost:9411');
  console.log('Prometheus: http://localhost:9090');
  console.log('══════════════════════════════════════════════\n');
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function randomUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
