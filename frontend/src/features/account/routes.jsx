import { LoginPage, RegisterPage } from './AuthPages.jsx'
import ProfilePage from './ProfilePage.jsx'
import { CartPage, CheckoutPage } from './CartPages.jsx'
import { OrderDetailPage, OrdersPage } from './OrderPages.jsx'

export default [
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  { path: '/profile', element: <ProfilePage />, auth: true },
  { path: '/cart', element: <CartPage />, auth: true },
  { path: '/checkout', element: <CheckoutPage />, auth: true },
  { path: '/orders', element: <OrdersPage />, auth: true },
  { path: '/orders/:orderNumber', element: <OrderDetailPage />, auth: true },
]

export const nav = [{ label: 'My orders', to: '/orders', auth: true }]
