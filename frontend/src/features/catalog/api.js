import { api } from '../../api/client.js'

export const fetchProducts = (params) => api.get('/products', { params, paramsSerializer: { indexes: null } }).then((r) => r.data)
export const fetchFilters = (category) => api.get('/products/filters', { params: { category } }).then((r) => r.data)
export const fetchCategories = () => api.get('/categories').then((r) => r.data)
export const fetchProduct = (sku) => api.get(`/products/${sku}`).then((r) => r.data)
export const fetchCompare = (skus) => api.get('/products/compare', { params: { skus: skus.join(',') } }).then((r) => r.data)
export const fetchWishlist = () => api.get('/wishlist').then((r) => r.data)
export const addWishlist = (sku) => api.post(`/wishlist/${sku}`).then((r) => r.data)
export const removeWishlist = (sku) => api.delete(`/wishlist/${sku}`)
// Contract with the cart slice: POST /api/cart/items {sku, quantity}
export const addToCart = (sku, quantity = 1) => api.post('/cart/items', { sku, quantity }).then((r) => r.data)
