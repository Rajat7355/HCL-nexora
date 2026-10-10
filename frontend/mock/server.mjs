// Tiny dependency-free mock API so the UI can be explored without Java/Postgres: `npm run dev:mock`.
import http from 'node:http'

const cats = [['laptops', 'Laptops'], ['phones', 'Phones'], ['headphones', 'Headphones']]
const products = Array.from({ length: 30 }, (_, i) => {
  const [slug, category] = cats[i % 3]
  return {
    productId: `${slug.slice(0, 3).toUpperCase()}${100 + i}`, name: `${['Nova', 'Apex', 'Zen'][i % 3]} ${category.slice(0, -1)} ${i + 1}`,
    brand: ['Nova', 'Apex', 'Zen'][i % 3], category, categorySlug: slug, price: 199 + i * 37, currency: 'USD',
    rating: 3.5 + (i % 15) / 10, ratingCount: 40 + i * 7, specs: JSON.stringify({ RAM: `${8 + (i % 3) * 8} GB` }),
    availableQty: i % 7 === 0 ? 3 : 20, availability: i % 7 === 0 ? 'LOW_STOCK' : 'IN_STOCK', imageUrl: null,
  }
})
const user = { id: 1, email: 'prime@shopsmart.test', firstName: 'Demo', lastName: 'Shopper', tier: 'PRIME', region: 'US', staff: false }
const json = (res, code, body) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(body)) }

http.createServer((req, res) => {
  const u = new URL(req.url, 'http://x'); const p = u.pathname.replace(/^\/api/, '')
  let body = ''
  req.on('data', (c) => (body += c))
  req.on('end', () => {
    if (p === '/products') {
      const q = (u.searchParams.get('q') || '').toLowerCase(); const cat = u.searchParams.get('category')
      const items = products.filter((x) => (!q || x.name.toLowerCase().includes(q)) && (!cat || x.categorySlug === cat))
      return json(res, 200, { items: items.slice(0, 12), page: 1, size: 12, total: items.length, totalPages: Math.ceil(items.length / 12) })
    }
    if (p === '/products/filters') return json(res, 200, { brands: [{ slug: 'nova', name: 'Nova', count: 10 }, { slug: 'apex', name: 'Apex', count: 10 }], price: { min: 199, max: 1300 } })
    if (p === '/categories') return json(res, 200, cats.map(([slug, name], i) => ({ id: i + 1, slug, name, parentId: null })))
    if (p.startsWith('/products/')) return json(res, 200, { ...products[0], description: 'Mock product.', images: [], specs: { RAM: '16 GB' } })
    if (p === '/auth/login' || p === '/auth/register') return json(res, 200, { accessToken: 'mock', refreshToken: 'mock', expiresIn: 900, user })
    if (p === '/me') return json(res, 200, user)
    if (p === '/assistant/recommend' || p === '/assistant/chat') {
      const recs = products.slice(0, 3).map((x) => ({ ...x, productName: x.name, reason: 'Mock recommendation within budget.' }))
      const rec = { interpretedRequirement: 'laptop under $1000', budget: { max: 1000 }, recommendations: recs, clarifyingQuestion: 'Do you need a lightweight model?' }
      return json(res, 200, p.endsWith('chat') ? { sessionId: 's1', intent: 'PRODUCT_RECOMMENDATION', text: 'Here are some options.', payload: rec } : rec)
    }
    if (p === '/knowledge/ask') return json(res, 200, { answer: 'Laptops can be returned within 15 days (mock).', confidence: 'HIGH', followUpRequired: false, sources: [{ document: 'Return Policy', section: 'Electronics', version: 2 }] })
    json(res, 200, [])
  })
}).listen(8090, () => console.log('mock API on :8090'))
