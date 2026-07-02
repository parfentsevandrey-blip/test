import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { getConditionInfo } from '../utils/weatherCondition'
import { formatTemperature, formatHour } from '../utils/units'

export function HourlyForecast(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const flooredHour = `${weather.current.time.slice(0, 13)}:00`
  const startIndex = weather.hourly.findIndex((h) => h.time === flooredHour)
  const points = weather.hourly.slice(startIndex === -1 ? 0 : startIndex, (startIndex === -1 ? 0 : startIndex) + 24)

  return (
    <BentoCard span="bento-hourly">
      <div className="hourly-forecast">
        <div className="hourly-title">Next 24 Hours</div>
        <div className="hourly-scroll">
          {points.map((point, index) => (
            <div className={'hourly-item' + (index === 0 ? ' is-now' : '')} key={point.time}>
              <span className="hourly-time">{index === 0 ? 'Now' : formatHour(point.time)}</span>
              <WeatherIcon
                condition={getConditionInfo(point.weatherCode).condition}
                isDay={point.isDay}
                className="hourly-icon"
              />
              <span className="hourly-temp">{formatTemperature(point.temperature, unit)}</span>
              <span className="hourly-pop">{point.precipitationProbability}%</span>
            </div>
          ))}
        </div>
      </div>
    </BentoCard>
  )
}
