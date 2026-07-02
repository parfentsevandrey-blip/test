export type WeatherCondition =
  | 'clear'
  | 'partly-cloudy'
  | 'cloudy'
  | 'fog'
  | 'drizzle'
  | 'rain'
  | 'snow'
  | 'thunderstorm'

export interface ConditionInfo {
  condition: WeatherCondition
  label: string
  /** 0 (none) - 1 (heaviest) baseline precipitation intensity for this code, refined by real precip amount */
  precipitationIntensity: number
}

const BASE_VISIBILITY: Record<WeatherCondition, number> = {
  clear: 1,
  'partly-cloudy': 0.95,
  cloudy: 0.85,
  fog: 0.12,
  drizzle: 0.75,
  rain: 0.65,
  snow: 0.6,
  thunderstorm: 0.5
}

/** 0 (can't see far) - 1 (perfectly clear) baseline visibility for a scene condition, used to drive fog density. */
export function getBaseVisibility(condition: WeatherCondition): number {
  return BASE_VISIBILITY[condition]
}

/**
 * Maps an Open-Meteo WMO weather code (https://open-meteo.com/en/docs) to a
 * scene condition. Shared by the API layer (labels), the 3D scene
 * (visual state) and the UI (icons) so all three always agree.
 */
export function getConditionInfo(code: number): ConditionInfo {
  switch (code) {
    case 0:
      return { condition: 'clear', label: 'Clear sky', precipitationIntensity: 0 }
    case 1:
      return { condition: 'clear', label: 'Mainly clear', precipitationIntensity: 0 }
    case 2:
      return { condition: 'partly-cloudy', label: 'Partly cloudy', precipitationIntensity: 0 }
    case 3:
      return { condition: 'cloudy', label: 'Overcast', precipitationIntensity: 0 }
    case 45:
      return { condition: 'fog', label: 'Fog', precipitationIntensity: 0 }
    case 48:
      return { condition: 'fog', label: 'Depositing rime fog', precipitationIntensity: 0 }
    case 51:
      return { condition: 'drizzle', label: 'Light drizzle', precipitationIntensity: 0.2 }
    case 53:
      return { condition: 'drizzle', label: 'Moderate drizzle', precipitationIntensity: 0.35 }
    case 55:
      return { condition: 'drizzle', label: 'Dense drizzle', precipitationIntensity: 0.5 }
    case 56:
      return { condition: 'drizzle', label: 'Light freezing drizzle', precipitationIntensity: 0.25 }
    case 57:
      return { condition: 'drizzle', label: 'Dense freezing drizzle', precipitationIntensity: 0.45 }
    case 61:
      return { condition: 'rain', label: 'Slight rain', precipitationIntensity: 0.35 }
    case 63:
      return { condition: 'rain', label: 'Moderate rain', precipitationIntensity: 0.6 }
    case 65:
      return { condition: 'rain', label: 'Heavy rain', precipitationIntensity: 0.9 }
    case 66:
      return { condition: 'rain', label: 'Light freezing rain', precipitationIntensity: 0.4 }
    case 67:
      return { condition: 'rain', label: 'Heavy freezing rain', precipitationIntensity: 0.85 }
    case 71:
      return { condition: 'snow', label: 'Slight snow fall', precipitationIntensity: 0.3 }
    case 73:
      return { condition: 'snow', label: 'Moderate snow fall', precipitationIntensity: 0.55 }
    case 75:
      return { condition: 'snow', label: 'Heavy snow fall', precipitationIntensity: 0.85 }
    case 77:
      return { condition: 'snow', label: 'Snow grains', precipitationIntensity: 0.25 }
    case 80:
      return { condition: 'rain', label: 'Slight rain showers', precipitationIntensity: 0.4 }
    case 81:
      return { condition: 'rain', label: 'Moderate rain showers', precipitationIntensity: 0.65 }
    case 82:
      return { condition: 'rain', label: 'Violent rain showers', precipitationIntensity: 1 }
    case 85:
      return { condition: 'snow', label: 'Slight snow showers', precipitationIntensity: 0.4 }
    case 86:
      return { condition: 'snow', label: 'Heavy snow showers', precipitationIntensity: 0.8 }
    case 95:
      return { condition: 'thunderstorm', label: 'Thunderstorm', precipitationIntensity: 0.7 }
    case 96:
      return { condition: 'thunderstorm', label: 'Thunderstorm, slight hail', precipitationIntensity: 0.8 }
    case 99:
      return { condition: 'thunderstorm', label: 'Thunderstorm, heavy hail', precipitationIntensity: 1 }
    default:
      return { condition: 'partly-cloudy', label: 'Unknown', precipitationIntensity: 0 }
  }
}
