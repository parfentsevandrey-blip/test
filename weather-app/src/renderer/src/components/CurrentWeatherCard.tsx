import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { getConditionInfo } from '../utils/weatherCondition'
import { formatTemperature } from '../utils/units'

export function CurrentWeatherCard(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const { condition, label } = getConditionInfo(weather.current.weatherCode)

  return (
    <BentoCard span="bento-hero">
      <div className="current-card">
        <div className="current-location">
          {weather.location.name}
          <span className="country">
            {weather.location.admin1 ? ', ' + weather.location.admin1 : ''}
            {weather.location.country ? ' - ' + weather.location.country : ''}
          </span>
        </div>
        <div className="current-main">
          <span className="current-temp">{formatTemperature(weather.current.temperature, unit)}</span>
          <WeatherIcon condition={condition} isDay={weather.current.isDay} className="current-icon" />
        </div>
        <div className="current-condition">{label}</div>
        <div className="current-feels-like">
          Feels like {formatTemperature(weather.current.apparentTemperature, unit)}
        </div>
      </div>
    </BentoCard>
  )
}
