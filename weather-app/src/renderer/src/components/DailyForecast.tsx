import { useWeatherStore } from '../store/useWeatherStore'
import { BentoCard } from './BentoCard'
import { WeatherIcon } from './WeatherIcon'
import { getConditionInfo } from '../utils/weatherCondition'
import { formatTemperature, formatWeekday } from '../utils/units'

export function DailyForecast(): JSX.Element | null {
  const weather = useWeatherStore((s) => s.weather)
  const unit = useWeatherStore((s) => s.unit)

  if (!weather) return null

  const globalMin = Math.min(...weather.daily.map((day) => day.tempMin))
  const globalMax = Math.max(...weather.daily.map((day) => day.tempMax))
  const span = globalMax - globalMin

  const pct = (temperature: number): number => (span === 0 ? 0 : ((temperature - globalMin) / span) * 100)

  return (
    <BentoCard span="bento-daily" floatDelay={0.3}>
      <div className="daily-forecast">
        <div className="hourly-title">10-Day Forecast</div>
        <div className="daily-rows">
          {weather.daily.map((day, index) => {
            const minPct = pct(day.tempMin)
            const maxPct = pct(day.tempMax)
            return (
              <div className="daily-row" key={day.date}>
                <span className="daily-day">{index === 0 ? 'Today' : formatWeekday(day.date)}</span>
                <WeatherIcon condition={getConditionInfo(day.weatherCode).condition} isDay={true} className="daily-icon" />
                <span className="daily-pop">{day.precipitationProbabilityMax}%</span>
                <div className="daily-range">
                  <span className="daily-temp-min">{formatTemperature(day.tempMin, unit)}</span>
                  <div className="daily-track">
                    <div
                      className="daily-track-fill"
                      style={{ left: `${minPct}%`, width: `${maxPct - minPct}%` }}
                    />
                  </div>
                  <span className="daily-temp-max">{formatTemperature(day.tempMax, unit)}</span>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </BentoCard>
  )
}
