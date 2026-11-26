import { MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'
import terraces from '../assets/terraces.jpg'
import stoneHouses from '../assets/stone-houses.jpg'
import courtyard from '../assets/courtyard.jpg'

const scenes = {
  battir: terraces,
  lifta: stoneHouses,
  'deir-ghassaneh': courtyard,
  artas: terraces,
  sebatya: stoneHouses,
  'ein-arik': courtyard,
}

export default function VillageCard({ village }) {
  return (
    <article className="village-card">
      <div className="card-scene">
        <img src={scenes[village.id] ?? terraces} alt="" loading="lazy" />
        <span>مشهد توضيحي</span>
      </div>
      <div className="card-body">
        <p className="location"><MapPin size={16} aria-hidden="true" /> محافظة {village.governorate}</p>
        <h3>{village.name}</h3>
        <Link className="card-link" to={`/villages/${village.id}`} aria-label={`استكشف قرية ${village.name}`}>استكشف القرية</Link>
      </div>
    </article>
  )
}
