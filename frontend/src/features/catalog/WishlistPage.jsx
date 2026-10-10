import { Link } from 'react-router-dom'
import { Empty, PageHeader, Spinner } from '../../shell/ui.jsx'
import ProductCard, { useWishlist } from './ProductCard.jsx'
import { useQuery } from '@tanstack/react-query'
import { fetchWishlist } from './api.js'

export default function WishlistPage() {
  useWishlist()
  const { data, isLoading } = useQuery({ queryKey: ['wishlist'], queryFn: fetchWishlist })
  return (
    <div>
      <PageHeader title="Your wishlist" subtitle="Products you saved for later." />
      {isLoading && <Spinner />}
      {data && data.length === 0 && <Empty title="Nothing saved yet">Tap the heart on any product. <Link to="/" className="text-brand-600 underline">Browse products</Link></Empty>}
      {data && data.length > 0 && <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{data.map((p) => <ProductCard key={p.productId} product={p} />)}</div>}
    </div>
  )
}
